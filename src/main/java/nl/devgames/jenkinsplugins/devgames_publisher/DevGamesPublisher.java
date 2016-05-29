package nl.devgames.jenkinsplugins.devgames_publisher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.exceptions.UnirestException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.sonar.SonarRunnerBuilder;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import nl.devgames.jenkinsplugins.devgames_publisher.deserializers.JenkinsObjectDeserializer;
import nl.devgames.jenkinsplugins.devgames_publisher.deserializers.SonarDuplicationsObjectDeserializer;
import nl.devgames.jenkinsplugins.devgames_publisher.deserializers.SonarIssuesObjectDeserializer;
import nl.devgames.jenkinsplugins.devgames_publisher.exceptions.*;
import nl.devgames.jenkinsplugins.devgames_publisher.helpers.Tuple;
import nl.devgames.jenkinsplugins.devgames_publisher.helpers.dateformatting.DateConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.helpers.http.PostReturnObject;
import nl.devgames.jenkinsplugins.devgames_publisher.helpers.http.URLRequester;
import nl.devgames.jenkinsplugins.devgames_publisher.models.JenkinsObject;
import nl.devgames.jenkinsplugins.devgames_publisher.models.ServerJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarDuplicationsObject;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarIssuesObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The DevGames publisher plugin.
 *
 * It must be configured in the Jenkins global settings and in the settings page for a project.<br><br>
 *
 * Requires:<br>
 * - A supported SCM plugin enabled for the project. This can be GIT or SVN.<br>
 * - A SonarQube plugin enabled. Can be in either pre-build or post-build steps.
 */
public class DevGamesPublisher extends Publisher implements SimpleBuildStep {

    private static final String RULE_ENGINE_LOCATION = "http://145.24.222.173:8080";        // Location where the rule engine can be found

    private final String TOKEN;                                                             // Project token which needs to be set in the configuration panel of the plugin
    private final String SONARQUBE_PROJECT_KEY;                                             // This also needs to be configured in the plugin panel. This is needed for the requests to the sonarqube API

    /**
     * Plugin constructor to be called by Jenkins. The parameters are automatically read from the plugin configuration and filled in by Jenkins.
     * @param TOKEN Token for the project. Is read from the plugin configuration panel and gets inserted automatically by Jenkins.
     * @param SONARQUBE_PROJECT_KEY SonarQube project key. Needed for the requests to the SonarQube API. Is read from the plugin configuration panel and gets inserted automatically by Jenkins.
     */
    @DataBoundConstructor
    public DevGamesPublisher(String TOKEN, String SONARQUBE_PROJECT_KEY) {
        this.TOKEN = TOKEN;
        this.SONARQUBE_PROJECT_KEY = SONARQUBE_PROJECT_KEY;
    }

    /**
     * Gets the configured project token. This method is needed by Jenkins.
     * @return The configured project token.
     */
    public String getTOKEN() {
        return TOKEN;
    }

    /**
     * Gets the configured SonarQube project key. This method is needed by Jenkins.
     * @return The configured SonarQube project key.
     */
    public String getSONARQUBE_PROJECT_KEY() {
        return SONARQUBE_PROJECT_KEY;
    }

    /**
     * Method that gets called from the Jenkins build system.
     * In this method all the requests to the Jenkins and SonarQube API's are done and converted to JSON which is send to the rule engine.
     * @param build Object containing data about the current build.
     * @param workspace File path to the workspace in which the build is started.
     * @param launcher Launcher object. This is an object to launch processes.
     * @param listener Listener object which is responsible for logging information to the build console.
     *
     * Also see {@link SimpleBuildStep#perform(hudson.model.Run, hudson.FilePath, hudson.Launcher, hudson.model.TaskListener)}
     */
    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        listener.getLogger().println("[INFO] Starting DevGames publisher");
        try {
            // Get the project from the current build and cast it to a maven project.
            // The plugin is only enabled on maven projects, so this shouldn't give any problems.
            MavenModuleSet project = ((MavenModuleSetBuild) build).getProject();

            listener.getLogger().println("[INFO] Checking for Git or SVN source control manager plugin");
            checkIfSupportedSCMIsActive(project);   // Check if the Git or SVN SCM plugins are enabled for this project. If neither of them are: give a SupportedSCMNotFoundException, which gets caught and markes the build as FAILURE.

            listener.getLogger().println("[INFO] Checking if SonarQube plugin is enabled");
            checkIfSonarQubePluginIsEnabled(project);   // Check if the SonarQube plugin is enabled for this project. If it isn't: give a SonarPluginNotFoundException, which gets caught and markes the build as FAILURE.

            String url                  = build.getUrl();       // Get the url for the project in the format of "job/{ProjectName}/{buildNr}/"
            String baseURL              = build.getEnvironment(listener).get("JENKINS_URL");    // Get the url for jenkins, this can be changed in the settings screen

            listener.getLogger().println("[INFO] Requesting Jenkins base url");
            if("".equals(baseURL))   // If we can't find the jenkins base url, we can't retrieve it;s API -> ABORT
                throw new JenkinsBaseURLNotFoundException("Could not find the jenkins url, please check the jenkins settings if isn't empty.");

            // Make a gsonBuilder and register our custom deserializers
            GsonBuilder gsonBuilder     = new GsonBuilder()
                                                .registerTypeAdapter(SonarDuplicationsObject.class, new SonarDuplicationsObjectDeserializer())
                                                .registerTypeAdapter(SonarIssuesObject.class, new SonarIssuesObjectDeserializer())
                                                .registerTypeAdapter(JenkinsObject.class, new JenkinsObjectDeserializer());
            Gson gson                   = gsonBuilder.create();

            // Get the Jenkins object from the Jenkins API
            listener.getLogger().println("[INFO] Parsing Jenkins API");
            JenkinsObject jenkinsObject = getJenkinsObjectFromAPI(baseURL, url, gson);  // Gets the JSON from the Jenkins API and converts it into a JenkinsObject, which makes it easier to get data from it later.
            String[] authorParts = jenkinsObject.getChangeSet().getItems().get(0).getAuthor().getScmUser().split("/");  // Gets the author in the format of <JenkinsBaseURL>/user/<author> and splits the string, since we are only interested in the last part.
            String pushAuthor = authorParts[authorParts.length-1];      // Get the last part of the author url
            listener.getLogger().println("[INFO] A changeset of kind '" + jenkinsObject.getChangeSet().getKind() + "' was found with " + jenkinsObject.getChangeSet().getItems().size() + " items");
            listener.getLogger().println("[INFO] Push was registered for user '" + pushAuthor + "'");
            listener.getLogger().println("[INFO] Jenkins build is marked as '" + jenkinsObject.getResult() + "' and was started at " + build.getTime());

            // Sleep for 10 seconds, to give SonarQube some time to upload it's report to the database and API
            listener.getLogger().println("[INFO] Sleeping for 10 seconds to give SonarQube some time to upload it's report");
            Thread.sleep(10000);

            // Get the jenkins build date in a format that SonarQube supports
            String jenkinsBuildDate     = DateConverter.convertDateToString(build.getTime(), DateConverter.SONARQUBE_DATE_FORMAT);
            // Get all the newly created issues since this build
            listener.getLogger().println("[INFO] Parsing new issues from the SonarQube API");
            SonarIssuesObject newSonarIssuesObject = getSonarQubeIssuesObjectFromAPI(jenkinsBuildDate, gson);
            listener.getLogger().println("[INFO] " + newSonarIssuesObject.getIssues().size() + " new issues were found");

            // Get the fixed issues since this build
            listener.getLogger().println("[INFO] Parsing fixed issues from the SonarQube API");
            SonarIssuesObject fixedSonarIssuesObject = getFixedSonarIssuesObjectFromAPI(build.getTime(), gson);
            listener.getLogger().println("[INFO] " + fixedSonarIssuesObject.getIssues().size() + " fixed issues were found");

            // Get the duplications since this build
            listener.getLogger().println("[INFO] Getting duplications associated with the new issues");
            List<SonarDuplicationsObject> sonarDuplications = getSonarDuplicationsFromNewIssues(newSonarIssuesObject, gson);
            listener.getLogger().println("[INFO] " + sonarDuplications.size() + " duplications were found");

            // Create an object for the json we need to return
            listener.getLogger().println("[INFO] Creating object to return to the server");
            ServerJsonObject serverJsonObject = new ServerJsonObject();
            serverJsonObject.setResult(build.getResult().toString());
            serverJsonObject.setTimestamp(new Date().getTime());
            serverJsonObject.setAuthor(pushAuthor);

            // Parse commits from the jenkins json object and convert them for the server json object
            listener.getLogger().println("[INFO] Adding changes found in the Jenkins API");
            List<ServerJsonObject.Item> items = jenkinsObject.getChangeSet().getItems().stream()
                    .map(item -> {
                        ServerJsonObject.Item newItem = serverJsonObject.new Item();
                        newItem.setCommitId(item.getCommitId());
                        newItem.setCommitMsg(item.getMsg());
                        newItem.setTimestamp(item.getDate().getTime());

                        return newItem;
                    }).collect(Collectors.toList());
            serverJsonObject.setItems(items);

            // Parse the new issues from SonarQube
            if(!newSonarIssuesObject.getIssues().isEmpty())
                listener.getLogger().println("[INFO] Adding the new issues found in the SonarQube API");
            List<ServerJsonObject.Issue> newIssues = newSonarIssuesObject.getIssues().stream().
                    map(issue -> {
                        ServerJsonObject.Issue newIssue = createServerJsonIssueFromSonarIssue(issue, false);
                        return newIssue;
                    }).collect(Collectors.toList());
            serverJsonObject.setIssues(newIssues);

            // Parse the fixed issues from SonarQube
            if(!fixedSonarIssuesObject.getIssues().isEmpty())
                listener.getLogger().println("[INFO] Adding the fixed issues found in the SonarQube API");
            List<ServerJsonObject.Issue> fixedIssues = fixedSonarIssuesObject.getIssues().stream().
                    map(issue -> {
                        ServerJsonObject.Issue newIssue = createServerJsonIssueFromSonarIssue(issue, true);
                        return newIssue;
                    }).collect(Collectors.toList());
            serverJsonObject.getIssues().addAll(fixedIssues);

            if(!sonarDuplications.isEmpty())
                listener.getLogger().println("[INFO] Adding the duplications found in the SonarQube API");
            List<ServerJsonObject.Duplication> duplications = getServerDuplicationsFromSonarDuplications(sonarDuplications);
            serverJsonObject.setDuplications(duplications);

            /*
            We are done with requesting and merging data from the API's.
            Send it to the rule engine
             */
            listener.getLogger().println("[INFO] Sending DevGames report to the rule engine");
            sendJsonObjectToRuleEngine(serverJsonObject, gson);

            listener.getLogger().println("[INFO] DONE");
        } catch (IOException |
                InterruptedException |
                JenkinsBaseURLNotFoundException |
                NoChangeSetFoundException |
                UnirestException |
                DatabaseOfflineException |
                TokenNotFoundException |
                KnownInternalServerErrorException |
                CouldNotRequestURLException |
                SupportedSCMNotFoundException |
                SonarPluginNotFoundException e) {
            //If any of the above exceptions get thrown, we need to mark the build as a failure and log the reason for the exception
            build.setResult(Result.FAILURE);
            listener.getLogger().println("[ERROR] " + e.getMessage());
        }

        // Log the outcome of the build. This can be SUCCESS or FAILURE
        listener.getLogger().println("[OUTPUT] Result: " + build.getResult());
    }

    /**
     * Method which requests the JSON about this build from the Jenkins API and converts in into a JenkinsObject to make it easier to get data from it.
     * @param jenkinsBaseURL The base url where Jenkins is installed. Can be configured in the Jenkins global setting. Most of the time this is http://localhost:8080/jenkins/
     * @param projectURL The url where the project job is located. This is in the format of job/<projectName>/<buildNr>/
     * @param gson A GSON object which has the JenkinsObject deserializer configured.
     * @return A JenkinsObject containing data about this project build.
     * @throws UnirestException An exception thrown from the underlying HTTP library. This is thrown if something went wrong during the request to the API, for example: the url is not found, there is no network connection, etc.
     * @throws NoChangeSetFoundException An exception which is thrown when there are no changes found in the build. This could happen on the first project build or when a build is started without committing new changes.
     */
    private JenkinsObject getJenkinsObjectFromAPI(String jenkinsBaseURL, String projectURL, Gson gson) throws UnirestException, NoChangeSetFoundException {
        String apiURL = jenkinsBaseURL + projectURL + "api/json";                   // The url that needs to be requested
        String json = URLRequester.getJsonFromURL(apiURL, null, null, null);        // Helper method that does the HTTP request an gives us the JSON from the response body
        JenkinsObject jenkinsObject = gson.fromJson(json, JenkinsObject.class);

        // No new changes could be found, throw an exception
        if (jenkinsObject.getChangeSet().getItems().isEmpty()) {
            throw new NoChangeSetFoundException("Could not find new changes.");
        }

        return jenkinsObject;
    }

    /**
     * Methods that takes a (non urlencoded) date and gson object which has the SonarQubeIssuesObject deserializer configured.
     * Requests the SonarQube API and returns the newly found issues since the given date.
     * @param date A string containing a date in the format yyyy-MM-dd'T'HH:mm:ssZ
     * @param gson A GSON object which has the SonarQubeIssuesObject deserializer configured.
     * @return A SonarIssuesObject containing the new issues found.
     * @throws UnirestException An exception thrown from the underlying HTTP library. This is thrown if something went wrong during the request to the API, for example: the url is not found, there is no network connection, etc.
     */
    private SonarIssuesObject getSonarQubeIssuesObjectFromAPI(String date, Gson gson) throws UnirestException {
        // Create a new Tuple object containing the SonarQube user and password. These are configured in the plugin panel in the Jenkins global settings.
        // This is needed because some parts of the API are secured.
        Tuple<String, String> basicAuthUsernameAndPassword = new Tuple<>(getDescriptor().getSONARQUBE_USER(), getDescriptor().getSONARQUBE_PASSWORD());
        String url = String.format("%s/api/issues/search?componentKeys=%s&resolved=false&createdAfter=%s&ps=%s",
                getDescriptor().getSONARQUBE_URL(), // The url where sonarqube can be found. Is configured in the plugin panel in the Jenkins global settings.
                "{sonarProjectKey}",
                "{urlEncodedDate}",
                "{pageSize}");

        // Fill in the route parameters and urlencode them
        Map<String, String> routeParams = new HashMap<>();
        routeParams.put("sonarProjectKey", getSONARQUBE_PROJECT_KEY());
        routeParams.put("urlEncodedDate", date);
        routeParams.put("pageSize", "500");

        // Do the request to the SonarQube API and return JSON containing the new issues
        String json = URLRequester.getJsonFromURL(url, null, routeParams, basicAuthUsernameAndPassword);
        SonarIssuesObject newSonarIssuesObject = gson.fromJson(json, SonarIssuesObject.class);

        // If we don't have all the issues yet, do additional requests and add the issues on the other pages to the SonarIssuesObject
        if(newSonarIssuesObject.getTotal() > newSonarIssuesObject.getPageSize()) {
            int numOfIssue = newSonarIssuesObject.getPageSize();
            int issuesLeft = newSonarIssuesObject.getTotal() - numOfIssue;
            int page = 2;
            url = String.format("%s&p=%s", url, "{page}");
            while(issuesLeft > 0) {
                routeParams.put("page", String.valueOf(page));
                json = URLRequester.getJsonFromURL(url, null, routeParams, basicAuthUsernameAndPassword);
                SonarIssuesObject tempSonarIssuesObject = gson.fromJson(json, SonarIssuesObject.class);
                newSonarIssuesObject.getIssues().addAll(tempSonarIssuesObject.getIssues());

                issuesLeft = numOfIssue - (page*500);
                page++;
            }
        }

        return newSonarIssuesObject;
    }

    /**
     * Method that takes a date after which issues must be fixed(this is always the Jenkins build date) and a GSON object with the SonarIssuesObject deserializer configured.
     * @param fixedAfter A date after which issues must be fixed. This is always the Jenkins build date
     * @param gson A GSON object with the SonarIssuesObject deserializer configured.
     * @return A SonarIssuesObject containing the fixed issues since the given date.
     * @throws UnirestException An exception thrown from the underlying HTTP library. This is thrown if something went wrong during the request to the API, for example: the url is not found, there is no network connection, etc.
     */
    private SonarIssuesObject getFixedSonarIssuesObjectFromAPI(Date fixedAfter, Gson gson) throws UnirestException {
        // Create a new Tuple object containing the SonarQube user and password. These are configured in the plugin panel in the Jenkins global settings.
        // This is needed because some parts of the API are secured.
        Tuple<String, String> basicAuthUsernameAndPassword = new Tuple<>(getDescriptor().getSONARQUBE_USER(), getDescriptor().getSONARQUBE_PASSWORD());
        String url = String.format("%s/api/issues/search?componentKeys=%s&resolved=true&ps=500&s=UPDATE_DATE&asc=false",
                getDescriptor().getSONARQUBE_URL(), // The url where sonarqube can be found. Is configured in the plugin panel in the Jenkins global settings.
                "{sonarProjectKey}");

        // Fills in the route parameters and urlencodes them
        Map<String, String> routeParams = new HashMap<>();
        routeParams.put("sonarProjectKey", getSONARQUBE_PROJECT_KEY());

        // Do the request to the SonarQube API and return JSON containing the fixed issues
        String json = URLRequester.getJsonFromURL(url, null, routeParams, basicAuthUsernameAndPassword);
        SonarIssuesObject fixedSonarIssuesObject = gson.fromJson(json, SonarIssuesObject.class);

        // Since the SonarQube API does not have the possibility to only get the fixed issues after a given date,
        // we need to get all the fixed issues and remove the ones that are not fixed after the given date.
        List<SonarIssuesObject.Issue> issuesToRemoveFromFixedIssuesObject =
                fixedSonarIssuesObject.getIssues().stream()
                        .filter(issue -> issue.getCloseDate().before(fixedAfter)).collect(Collectors.toList());
        fixedSonarIssuesObject.getIssues().removeAll(issuesToRemoveFromFixedIssuesObject);

        return fixedSonarIssuesObject;
    }

    /**
     * A method that takes a SonarIssuesObject containing issues and a GSON object with the SonarDuplicationsObject deserializer configured. Returns a list of SonarDuplicationObject
     * @param newIssuesObject A SonarIssuesObject containing the new issues.
     * @param gson A GSON object with the SonarDuplicationsObject deserializer configured.
     * @return A List of SonarDuplicationObject containing the duplications found in the issues.
     */
    private List<SonarDuplicationsObject> getSonarDuplicationsFromNewIssues(SonarIssuesObject newIssuesObject, Gson gson){
        // Create a new Tuple object containing the SonarQube user and password. These are configured in the plugin panel in the Jenkins global settings.
        // This is needed because some parts of the API are secured.
        Tuple<String, String> basicAuthUsernameAndPassword = new Tuple<>(getDescriptor().getSONARQUBE_USER(), getDescriptor().getSONARQUBE_PASSWORD());

        // For each of the issues containing the "common-java:DuplicatedBlocks" rule, request the SonarQube duplications API
        // Returns a List of SonarDuplicationsObject
        return newIssuesObject.getIssues().parallelStream()
                .filter(issue -> "common-java:DuplicatedBlocks".equals(issue.getRule()))
                .map(issue -> {
                    try {
                        String url = String.format(
                                getDescriptor().getSONARQUBE_URL() + "/api/duplications/show?key=%s", issue.getComponent());

                        String json = URLRequester.getJsonFromURL(url, null, null, basicAuthUsernameAndPassword);
                        return gson.fromJson(json, SonarDuplicationsObject.class);
                    } catch (UnirestException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.toList());
    }

    /**
     * Converts a SonarIssueObject.Issue to a ServerJsonObject.Issue
     * @param issue The issue to convert
     * @param isFixedIssue Is this a fixed issue?
     * @return The newly created ServerJsonObject issue
     */
    private ServerJsonObject.Issue createServerJsonIssueFromSonarIssue(SonarIssuesObject.Issue issue, boolean isFixedIssue) {
        ServerJsonObject serverJsonObject = new ServerJsonObject();
        ServerJsonObject.Issue newIssue = serverJsonObject.new Issue();
        newIssue.setKey(issue.getKey());
        newIssue.setSeverity(issue.getSeverity());
        newIssue.setComponent(issue.getComponent());
        newIssue.setStatus(issue.getStatus());
        newIssue.setResolution(issue.getResolution());
        newIssue.setMessage(issue.getMessage());

        if (issue.getCreationDate() != null)
            newIssue.setCreationDate(issue.getCreationDate().getTime());

        if (isFixedIssue) {
            if (issue.getUpdateDate() != null)
                newIssue.setUpdateDate(issue.getUpdateDate().getTime());
            if (issue.getCloseDate() != null)
                newIssue.setCloseDate(issue.getCloseDate().getTime());
        }

        if (issue.getTextRange() != null) {
            newIssue.setStartLine(issue.getTextRange().getStartLine());
            newIssue.setEndLine(issue.getTextRange().getEndLine());
        }

        int debt = 0;
        // Takes the debt string, removes the non-numerical characters and converts it into an int.
        // If the debt string contains an "h", convert in into minutes
        if (issue.getDebt().contains("h")) {
            int positionH = issue.getDebt().indexOf("h");
            int hours = Integer.parseInt(issue.getDebt().substring(0, positionH));

            if (positionH != issue.getDebt().length()-1){
                int posMIN = issue.getDebt().indexOf("m");
                int minutes = Integer.parseInt(issue.getDebt().substring(positionH+1, posMIN));
                debt = (hours*60)+minutes;
            } else {
                debt = hours*60;
            }
        } else {
            int posMIN = issue.getDebt().indexOf("m");
            int minutes = Integer.parseInt(issue.getDebt().substring(0, posMIN));
            debt = minutes;
        }
        newIssue.setDebt(debt);
        return newIssue;
    }

    /**
     * Converts the SonarQube duplications list into a list compatible with the server.
     * @param duplications A list of SonarDuplication objects.
     * @return The converted list into a format that the server can work with.
     */
    private List<ServerJsonObject.Duplication> getServerDuplicationsFromSonarDuplications(List<SonarDuplicationsObject> duplications){
        ServerJsonObject serverJsonObject = new ServerJsonObject(); // Make a temporary serverJsonObject to instantiate inner classes

        // Return the converted duplications list
        return duplications.stream().map(duplication -> {
            // Instantiate a new ServerJsonObject duplication
            ServerJsonObject.Duplication newDuplication = serverJsonObject.new Duplication();

            // Loop through all the duplications in the sonarqube duplications object
            for (SonarDuplicationsObject.Duplication sonarDuplication : duplication.getDuplications()){
                // Loop through all the files in the duplication
                List<ServerJsonObject.Duplication.File> duplicationFiles =
                        sonarDuplication.getBlocks().stream()
                        .map(block -> {
                            // Create for every block in the sonar duplications object a new "Duplication.File" for the serverJsonObject
                            ServerJsonObject.Duplication.File duplicationFile = newDuplication.new File();
                            duplicationFile.setBeginLine(block.getFrom());
                            duplicationFile.setSize(block.getSize());

                            // Find the file belonging to this block
                            SonarDuplicationsObject.File blockFile = null;
                            for (SonarDuplicationsObject.File file : duplication.getFiles()) {
                                // If we find the correct file belonging to this block, set the reference and break out of the loop
                                if (file.getRef().equals(block.get_ref())) {
                                    blockFile = file;
                                    break;
                                }
                            }
                            duplicationFile.setFile(blockFile.getName());
                            return duplicationFile;
                        }).collect(Collectors.toList());

                // Add the new files to the duplication object
                newDuplication.setFiles(duplicationFiles);
            }
            return newDuplication;
        }).collect(Collectors.toList());
    }

    /**
     * Converts the ServerJsonObject into JSON and sends it to the rule engine at {RULE_ENGINE_LOCATION}/{token}/build
     * @param jsonObject The ServerJsonObject which needs to be converted into JSON and is send to the rule engine
     * @param gson A Gson object which needs to convert the ServerJsonObject into JSON
     * @throws CouldNotRequestURLException An exception which is thrown if the url could not be requested for some reason
     * @throws TokenNotFoundException An exception which is thrown if the specified token is not found or not correct
     * @throws KnownInternalServerErrorException An exception which is thrown if an InternalServerError has occured on the rule engine.
     * @throws DatabaseOfflineException An exception which is thrown when the data could not be saved because the database is offline.
     */
    private void sendJsonObjectToRuleEngine(ServerJsonObject jsonObject, Gson gson) throws CouldNotRequestURLException, TokenNotFoundException, KnownInternalServerErrorException, DatabaseOfflineException {
        try {
            String json = gson.toJson(jsonObject);  // Convert the object into JSON
            String url = RULE_ENGINE_LOCATION + "/projects/{token}/build";

            // Fill in the route paramters and, if needed, urlencode them
            Map<String, String> routeParams = new HashMap<>();
            routeParams.put("token", getTOKEN());

            // POST the JSON to the rule engine and get an object with information about the request(status codes & body)
            PostReturnObject response = URLRequester.postJsonToURL(json, url, null, routeParams, null);

            // Check if the request was received corectly
            if(response.getStatusCode() == 404) {
                throw new TokenNotFoundException("The given token could not be found in the database, please check if it is typed right.");
            } else if(response.getStatusCode() == 503) {
                throw new DatabaseOfflineException("Database is offline");
            } else if(response.getStatusCode() != 200) {
                throw new KnownInternalServerErrorException("Error when parsing report");
            }
        } catch (UnirestException e) {
            throw new CouldNotRequestURLException("Could not request rule engine URL");
        } catch (DatabaseOfflineException | TokenNotFoundException | KnownInternalServerErrorException e) {
            throw e;
        }
    }

    /**
     *  Checks if a Supported(GIT or SVN) SCM plugin is enabled for this project, throws an exception if it doesn't.
     * @param project The maven project for which the methods needs to check if there is a supported SCM plugin.
     * @throws SupportedSCMNotFoundException Thrown when no supported SCM plugin could be found
     */
    private void checkIfSupportedSCMIsActive(MavenModuleSet project) throws SupportedSCMNotFoundException {
        SCM scm = project.getScm();
        if(!(scm instanceof GitSCM) && !(scm instanceof SubversionSCM)) {
            throw new SupportedSCMNotFoundException("Could not find the Git or SVN source control manager");
        }
    }

    /**
     * Checks if the SonarQube plugin is enabled for the given project, throws an exception if it doesn't.
     * @param project The maven project for which the method must check if there is a SonarQube plugin enabled
     * @throws SonarPluginNotFoundException Thrown when no SonarPlugin could be found for the given project.
     */
    private void checkIfSonarQubePluginIsEnabled(MavenModuleSet project) throws SonarPluginNotFoundException {
        boolean preBuildersListHasSonarPlugin = false;
        for (Builder builder : project.getPrebuilders()) {
            if(builder instanceof SonarRunnerBuilder){
                preBuildersListHasSonarPlugin = true;
                break;
            }
        }

        boolean postBuildersListHasSonarPlugin = false;
        for (Builder builder : project.getPostbuilders()) {
            if(builder instanceof SonarRunnerBuilder){
                postBuildersListHasSonarPlugin = true;
                break;
            }
        }

        if(!preBuildersListHasSonarPlugin && !postBuildersListHasSonarPlugin){
            throw new SonarPluginNotFoundException("Could not find the SonarQube plugin, please make sure you activated it as a pre-build or post-build plugin");
        }
    }

    /**
     * Gets the descriptor for this plugin
     * @return The descriptor implementation for this plugin
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Sets which BuildStepMonitor needs to be enabled for this plugin, we don't need one so we return BuildStepMonitor.NONE
     * @return BuildStepMonitor.NONE
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * The descriptor implementation for the DevGames plugin
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        // fields which are configured in the Jenkins global settings
        private String SONARQUBE_URL;
        private String SONARQUBE_USER;
        private String SONARQUBE_PASSWORD;

        public DescriptorImpl(){
            // Calls the load method, which gets the fields from the Jenkins global settings.
            load();
        }

        /**
         * Handles configuring the plugin with the values from the Jenkins global settings<br><br>
         * See {@link Descriptor#configure(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)}
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindJSON(this, json);

            save();
            return super.configure(req, json);
        }

        /**
         * Checks the specified project token. The token must be at least 12 characters long
         */
        public FormValidation doCheckTOKEN(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in your project token");
            if (value.length() < 12)
                return FormValidation.warning("Token is too short");
            return FormValidation.ok();
        }

        /**
         * Checks the specified SonarQube url. The url must be in the format of http://{host}:{port}
         */
        public FormValidation doCheckSONARQUBE_URL(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please specify where we can find SonarQube");
            if (value.length() < 12)
                return FormValidation.warning("URL is too short");
            if (!value.matches("(http://.*)"))
                return FormValidation.error("The URL needs to be in the format http://{host}:{port}");
            if (value.endsWith("/"))
                return FormValidation.error("The URL may not end with a slash");
            return FormValidation.ok();
        }

        /**
         * Checks the specified SonarQube user
         */
        public FormValidation doCheckSONARQUBE_USER(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in your SonarQube user");
            return FormValidation.ok();
        }

        /**
         * Checks the specified password for the given SonarQube user
         */
        public FormValidation doCheckSONARQUBE_PASSWORD(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in the password for the SonarQube user");
            return FormValidation.ok();
        }

        /**
         * Checks the specified SonarQube project key
         */
        public FormValidation doCheckSONARQUBE_PROJECT_KEY(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in your SonarQube project key");
            return FormValidation.ok();
        }

        /**
         * Checks if the plugin can be run on the given project class
         * @param aClass The given project class
         * @return true if the project is a maven project, false otherwise
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return MavenModuleSet.class.equals(aClass);
        }

        /**
         * Gets the string which is displayed when selecting the plugin in the publishers dropdown list
         */
        @Override
        public String getDisplayName() {
            return "Run DevGames publish task";
        }

        /**
         * Gets the specified SonarQube url
         * @return The specified SonarQube url
         */
        public String getSONARQUBE_URL() {
            return SONARQUBE_URL;
        }

        /**
         * Gets the specified SonarQube user
         * @return The specified SonarQube user
         */
        public String getSONARQUBE_USER() {
            return SONARQUBE_USER;
        }

        /**
         * Gets the specified password for the SonarQube user
         * @return The specified password
         */
        public String getSONARQUBE_PASSWORD() {
            return SONARQUBE_PASSWORD;
        }

        /**
         * Sets the given SonarQube url
         * @param SONARQUBE_URL A string containing a url to the SonarQube server
         */
        public void setSONARQUBE_URL(String SONARQUBE_URL) {
            this.SONARQUBE_URL = SONARQUBE_URL;
        }

        /**
         * Sets the given SonarQube user
         * @param SONARQUBE_USER A string containing the user for SonarQube
         */
        public void setSONARQUBE_USER(String SONARQUBE_USER) {
            this.SONARQUBE_USER = SONARQUBE_USER;
        }

        /**
         * Sets the given password for the SonarQube user
         * @param SONARQUBE_PASSWORD A string containing the password for the SonarQube user
         */
        public void setSONARQUBE_PASSWORD(String SONARQUBE_PASSWORD) {
            this.SONARQUBE_PASSWORD = SONARQUBE_PASSWORD;
        }
    }
}

