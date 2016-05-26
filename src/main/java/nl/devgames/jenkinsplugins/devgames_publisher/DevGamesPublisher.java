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

public class DevGamesPublisher extends Publisher implements SimpleBuildStep {

//    private static final String RULE_ENGINE_LOCATION = "http://145.24.222.173:8080";
    private static final String RULE_ENGINE_LOCATION = "http://localhost:8090";

    private final String TOKEN;
    private final String SONARQUBE_PROJECT_KEY;

    @DataBoundConstructor
    public DevGamesPublisher(String TOKEN, String SONARQUBE_PROJECT_KEY) {
        this.TOKEN = TOKEN;
        this.SONARQUBE_PROJECT_KEY = SONARQUBE_PROJECT_KEY;
    }

    public String getTOKEN() {
        return TOKEN;
    }

    public String getSONARQUBE_PROJECT_KEY() {
        return SONARQUBE_PROJECT_KEY;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        listener.getLogger().println("[INFO] Starting DevGames publisher");
        try {
            MavenModuleSet project = ((MavenModuleSetBuild) build).getProject();

            listener.getLogger().println("[INFO] Checking for Git or SVN source control manager plugin");
            checkIfSupportedSCMIsActive(project);

            listener.getLogger().println("[INFO] Checking if SonarQube plugin is enabled");
            checkIfSonarQubePluginIsEnabled(project);

            String url                  = build.getUrl();       // Get the url for the project in the format of "job/{ProjectName}/{buildNr}/"
            String baseURL              = build.getEnvironment(listener).get("JENKINS_URL");    // Get the url for jenkins, this can be changed in the settings screen

            listener.getLogger().println("[INFO] Requesting Jenkins base url");
            if("".equals(baseURL))   // If we can't find the jenkins base url, we can't retrieve it;s API -> ABORT
                throw new JenkinsBaseURLNotFoundException("Could not find the jenkins url, please check the jenkins settings if isn't empty.");

            // Make a gsonBuilder and register out custom deserializers
            GsonBuilder gsonBuilder     = new GsonBuilder()
                                                .registerTypeAdapter(SonarDuplicationsObject.class, new SonarDuplicationsObjectDeserializer())
                                                .registerTypeAdapter(SonarIssuesObject.class, new SonarIssuesObjectDeserializer())
                                                .registerTypeAdapter(JenkinsObject.class, new JenkinsObjectDeserializer());
            Gson gson                   = gsonBuilder.create();

            // Get the Jenkins object from the Jenkins API
            listener.getLogger().println("[INFO] Parsing Jenkins API");
            JenkinsObject jenkinsObject = getJenkinsObjectFromAPI(baseURL, url, gson);
            String[] authorParts = jenkinsObject.getChangeSet().getItems().get(0).getAuthor().getScmUser().split("/");
            String pushAuthor = authorParts[authorParts.length-1];
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
            We are done with requesting and merging date from the API's.
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
            build.setResult(Result.FAILURE);
            listener.getLogger().println("[ERROR] " + e.getMessage());
        }

        listener.getLogger().println("[OUTPUT] Result: " + build.getResult());
    }

    /**
     *
     * @param jenkinsBaseURL
     * @param projectURL
     * @param gson
     * @return
     * @throws UnirestException
     * @throws NoChangeSetFoundException
     */
    private JenkinsObject getJenkinsObjectFromAPI(String jenkinsBaseURL, String projectURL, Gson gson) throws UnirestException, NoChangeSetFoundException {
        String apiURL = jenkinsBaseURL + projectURL + "api/json";
        String json = URLRequester.getJsonFromURL(apiURL, null, null, null);
        JenkinsObject jenkinsObject = gson.fromJson(json, JenkinsObject.class);

        if (jenkinsObject.getChangeSet().getItems().isEmpty()) {
            throw new NoChangeSetFoundException("Could not find new changes.");
        }

        return jenkinsObject;
    }

    /**
     *
     * @param urlEncodedDate
     * @param gson
     * @return
     * @throws UnirestException
     */
    private SonarIssuesObject getSonarQubeIssuesObjectFromAPI(String urlEncodedDate, Gson gson) throws UnirestException {
        Tuple<String, String> basicAuthUsernameAndPassword = new Tuple<>(getDescriptor().getSONARQUBE_USER(), getDescriptor().getSONARQUBE_PASSWORD());
        String url = String.format("%s/api/issues/search?componentKeys=%s&resolved=false&createdAfter=%s&ps=%s",
                getDescriptor().getSONARQUBE_URL(),
                "{sonarProjectKey}",
                "{urlEncodedDate}",
                "{pageSize}");

        Map<String, String> routeParams = new HashMap<>();
        routeParams.put("sonarProjectKey", getSONARQUBE_PROJECT_KEY());
        routeParams.put("urlEncodedDate", urlEncodedDate);
        routeParams.put("pageSize", "500");
        String json = URLRequester.getJsonFromURL(url, null, routeParams, basicAuthUsernameAndPassword);
        SonarIssuesObject newSonarIssuesObject = gson.fromJson(json, SonarIssuesObject.class);

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
     *
     * @param fixedAfter
     * @param gson
     * @return
     * @throws UnirestException
     */
    private SonarIssuesObject getFixedSonarIssuesObjectFromAPI(Date fixedAfter, Gson gson) throws UnirestException {
        Tuple<String, String> basicAuthUsernameAndPassword = new Tuple<>(getDescriptor().getSONARQUBE_USER(), getDescriptor().getSONARQUBE_PASSWORD());
        String url = String.format("%s/api/issues/search?componentKeys=%s&resolved=true&ps=500&s=UPDATE_DATE&asc=false",
                getDescriptor().getSONARQUBE_URL(),
                "{sonarProjectKey}");

        Map<String, String> routeParams = new HashMap<>();
        routeParams.put("sonarProjectKey", getSONARQUBE_PROJECT_KEY());

        String json = URLRequester.getJsonFromURL(url, null, routeParams, basicAuthUsernameAndPassword);
        SonarIssuesObject fixedSonarIssuesObject = gson.fromJson(json, SonarIssuesObject.class);

        List<SonarIssuesObject.Issue> issuesToRemoveFromFixedIssuesObject =
                fixedSonarIssuesObject.getIssues().stream()
                        .filter(issue -> issue.getCloseDate().before(fixedAfter)).collect(Collectors.toList());
        fixedSonarIssuesObject.getIssues().removeAll(issuesToRemoveFromFixedIssuesObject);

        return fixedSonarIssuesObject;
    }

    /**
     *
     * @param newIssuesObject
     * @param gson
     * @return
     */
    private List<SonarDuplicationsObject> getSonarDuplicationsFromNewIssues(SonarIssuesObject newIssuesObject, Gson gson){
        Tuple<String, String> basicAuthUsernameAndPassword = new Tuple<>(getDescriptor().getSONARQUBE_USER(), getDescriptor().getSONARQUBE_PASSWORD());

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
     *
     * @param issue The issue to convert
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
        if (issue.getDebt().contains("h")) {
            int positionH = issue.getDebt().indexOf("h");
            int hours = Integer.parseInt(issue.getDebt().substring(0, positionH));

            if (positionH != issue.getDebt().length()-1){
                int posMIN = issue.getDebt().indexOf("m");
                int minutes = Integer.parseInt(issue.getDebt().substring(positionH+1, posMIN));
                debt = (hours*60)+minutes;
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
     *
     * @param duplications
     * @return
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
     *
     * @param jsonObject
     * @param gson
     * @throws CouldNotRequestURLException
     * @throws TokenNotFoundException
     * @throws KnownInternalServerErrorException
     * @throws DatabaseOfflineException
     */
    private void sendJsonObjectToRuleEngine(ServerJsonObject jsonObject, Gson gson) throws CouldNotRequestURLException, TokenNotFoundException, KnownInternalServerErrorException, DatabaseOfflineException {
        try {
            String json = gson.toJson(jsonObject);

            String url = RULE_ENGINE_LOCATION + "/projects/{token}/build";

            Map<String, String> routeParams = new HashMap<>();
            routeParams.put("token", getTOKEN());
            PostReturnObject response = URLRequester.postJsonToURL(json, url, null, routeParams, null);

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

    private void checkIfSupportedSCMIsActive(MavenModuleSet project) throws SupportedSCMNotFoundException {
        SCM scm = project.getScm();
        if(!(scm instanceof GitSCM) && !(scm instanceof SubversionSCM)) {
            throw new SupportedSCMNotFoundException("Could not find the Git or SVN source control manager");
        }
    }

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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String SONARQUBE_URL;
        private String SONARQUBE_USER;
        private String SONARQUBE_PASSWORD;

        public DescriptorImpl(){
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindJSON(this, json);

            save();
            return super.configure(req, json);
        }

        public FormValidation doCheckTOKEN(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in your project token");
            if (value.length() < 12)
                return FormValidation.warning("Token is too short");
            return FormValidation.ok();
        }

        public FormValidation doCheckSONARQUBE_URL(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please specify where we can find SonarQube");
            if (value.length() < 17)
                return FormValidation.warning("URL is too short");
            if (!value.matches("(http://.*)"))
                return FormValidation.error("The URL needs to be in the format http://{host}:{port}");
            if (value.endsWith("/"))
                return FormValidation.error("The URL may not end with a slash");
            return FormValidation.ok();
        }

        public FormValidation doCheckSONARQUBE_USER(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in your SonarQube user");
            return FormValidation.ok();
        }

        public FormValidation doCheckSONARQUBE_PASSWORD(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in the password for the SonarQube user");
            return FormValidation.ok();
        }

        public FormValidation doCheckSONARQUBE_PROJECT_KEY(@QueryParameter String value) {
            if (value.length() == 0)
                return FormValidation.error("Please fill in your SonarQube project key");
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return MavenModuleSet.class.equals(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Run DevGames publish task";
        }

        public String getSONARQUBE_URL() {
            return SONARQUBE_URL;
        }

        public String getSONARQUBE_USER() {
            return SONARQUBE_USER;
        }

        public String getSONARQUBE_PASSWORD() {
            return SONARQUBE_PASSWORD;
        }

        public void setSONARQUBE_URL(String SONARQUBE_URL) {
            this.SONARQUBE_URL = SONARQUBE_URL;
        }

        public void setSONARQUBE_USER(String SONARQUBE_USER) {
            this.SONARQUBE_USER = SONARQUBE_USER;
        }

        public void setSONARQUBE_PASSWORD(String SONARQUBE_PASSWORD) {
            this.SONARQUBE_PASSWORD = SONARQUBE_PASSWORD;
        }
    }
}

