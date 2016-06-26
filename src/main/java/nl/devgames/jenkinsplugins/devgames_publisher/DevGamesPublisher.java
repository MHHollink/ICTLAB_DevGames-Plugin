package nl.devgames.jenkinsplugins.devgames_publisher;
import com.google.gson.Gson;
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
import nl.devgames.dateconverters.DateConverter;
import nl.devgames.dateconverters.SonarQubeDateConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.apirequesters.APIRequester;
import nl.devgames.jenkinsplugins.devgames_publisher.apirequesters.JenkinsAPIRequester;
import nl.devgames.jenkinsplugins.devgames_publisher.apirequesters.SonarQubeDuplicationsAPIRequester;
import nl.devgames.jenkinsplugins.devgames_publisher.apirequesters.SonarQubeIssuesAPIRequester;
import nl.devgames.jenkinsplugins.devgames_publisher.exceptions.*;
import nl.devgames.jenkinsplugins.devgames_publisher.modelconverters.JenkinsItemToServerItemConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.modelconverters.ModelConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.modelconverters.SonarQubeDuplicationToServerDuplicationConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.modelconverters.SonarQubeIssueToServerIssueConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.models.JenkinsAPIData;
import nl.devgames.jenkinsplugins.devgames_publisher.models.ServerJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarDuplications;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarIssues;
import nl.devgames.urlrequesters.GetURLRequester;
import nl.devgames.urlrequesters.PostURLRequester;
import nl.devgames.urlrequesters.Response;
import nl.devgames.urlrequesters.UnirestURLRequester;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
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

    private static final String RULE_ENGINE_LOCATION = "http://localhost:8080";
//    private static final String RULE_ENGINE_LOCATION = "http://145.24.222.173:8080";

    // Project token which needs to be set in the configuration panel of the plugin
    private final String TOKEN;
    // This also needs to be configured in the plugin panel. This is needed for the requests to the sonarqube API
    private final String SONARQUBE_PROJECT_KEY;

    private MavenModuleSet project;
    private String projectUrl;
    private String jenkinsUrl;

    private JenkinsAPIData jenkinsAPIData;
    private String pushAuthor;
    private SonarIssues newSonarIssues;
    private SonarIssues fixedSonarIssues;
    private List<SonarDuplications> sonarDuplications;
    private ServerJsonObject serverJsonObject;

    private APIRequester<JenkinsAPIData> jenkinsAPIDataRequester;
    private APIRequester<SonarIssues> sonarIssuesAPIRequester;
    private APIRequester<SonarDuplications> sonarDuplicationsAPIRequester;

    private ModelConverter<SonarIssues.Issue, ServerJsonObject.Issue> sonarIssueToServerIssueConverter;
    private ModelConverter<JenkinsAPIData.ChangeSet.Item, ServerJsonObject.Item> jenkinsItemToServerItemConverter;
    private ModelConverter<SonarDuplications, ServerJsonObject.Duplication> sonarDuplicationToServerDuplicationConverter;

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

    public String getTOKEN() {
        return TOKEN;
    }

    public String getSONARQUBE_PROJECT_KEY() {
        return SONARQUBE_PROJECT_KEY;
    }

    /**
     * Method that gets called from the Jenkins build system.
     * In this method all the requests to the Jenkins and SonarQube API's are done and converted to JSON which is send to the rule engine.<br>
     * Also see {@link SimpleBuildStep#perform(hudson.model.Run, hudson.FilePath, hudson.Launcher, hudson.model.TaskListener)}
     */
    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        registerAPIRequesters();
        registerModelConverters();

        listener.getLogger().println("[INFO] Starting DevGames publisher");
        try {
            project = ((MavenModuleSetBuild) build).getProject();

            // Check if the Git or SVN SCM plugins are enabled for this project.
            // If neither of them are: give a SupportedSCMNotFoundException, which gets caught and markes the build as FAILURE.
            listener.getLogger().println("[INFO] Checking for Git or SVN source control manager plugin");
            checkIfSupportedSCMIsActive(project);

            // Check if the SonarQube plugin is enabled for this project.
            // If it isn't: give a SonarPluginNotFoundException, which gets caught and markes the build as FAILURE.
            listener.getLogger().println("[INFO] Checking if SonarQube plugin is enabled");
            checkIfSonarQubePluginIsEnabled(project);

            projectUrl      = build.getUrl();
            jenkinsUrl      = build.getEnvironment(listener).get("JENKINS_URL");

            listener.getLogger().println("[INFO] Requesting Jenkins base url");
            if("".equals(jenkinsUrl))
                throw new JenkinsBaseURLNotFoundException("Could not find the Jenkins url, please check the Jenkins settings.");

            listener.getLogger().println("[INFO] Parsing Jenkins API");
            getJenkinsDataFromAPI(jenkinsUrl, projectUrl, jenkinsAPIDataRequester);
            listener.getLogger().println("[INFO] A changeset of kind '" + jenkinsAPIData.getChangeSet().getKind() + "' was found with " + jenkinsAPIData.getChangeSet().getItems().size() + " items");
            listener.getLogger().println("[INFO] Push was registered for user '" + pushAuthor + "'");
            listener.getLogger().println("[INFO] Jenkins build is marked as '" + jenkinsAPIData.getResult() + "' and was started at " + build.getTime());

            listener.getLogger().println("[INFO] Sleeping for 10 seconds to give SonarQube some time to upload it's report");
            Thread.sleep(10000);

            // Get the jenkins build date in a format that SonarQube supports
            String jenkinsBuildDate                         = new SonarQubeDateConverter().convertDateToString(build.getTime());

            listener.getLogger().println("[INFO] Parsing new issues from the SonarQube API");
            getNewSonarQubeIssuesFromAPI(jenkinsBuildDate, sonarIssuesAPIRequester);
            listener.getLogger().println("[INFO] " + newSonarIssues.getIssues().size() + " new issues were found");

            // Get the fixed issues since this build
            listener.getLogger().println("[INFO] Parsing fixed issues from the SonarQube API");
            getFixedSonarIssuesObjectFromAPI(build.getTime(), sonarIssuesAPIRequester);
            listener.getLogger().println("[INFO] " + fixedSonarIssues.getIssues().size() + " fixed issues were found");

            // Get the duplications since this build
            listener.getLogger().println("[INFO] Getting duplications associated with the new issues");
            getSonarDuplicationsFromNewIssues(newSonarIssues, sonarDuplicationsAPIRequester);
            listener.getLogger().println("[INFO] " + sonarDuplications.size() + " duplications were found");

            // Create an object for the json we need to return
            listener.getLogger().println("[INFO] Creating object to return to the server");
            setupServerJsonObject(build, listener, sonarIssueToServerIssueConverter, jenkinsItemToServerItemConverter, sonarDuplicationToServerDuplicationConverter);

            listener.getLogger().println("[INFO] Sending DevGames report to the rule engine");
            sendJsonObjectToRuleEngine(serverJsonObject, new UnirestURLRequester());

            listener.getLogger().println("[INFO] DONE");
        } catch (IOException |
                InterruptedException |
                JenkinsBaseURLNotFoundException |
                NoChangeSetFoundException |
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

    private void registerModelConverters() {
        jenkinsItemToServerItemConverter                = new JenkinsItemToServerItemConverter();
        sonarDuplicationToServerDuplicationConverter    = new SonarQubeDuplicationToServerDuplicationConverter();
        sonarIssueToServerIssueConverter                = new SonarQubeIssueToServerIssueConverter();
    }

    private void registerAPIRequesters() {
        GetURLRequester urlRequester                    = new UnirestURLRequester();
        jenkinsAPIDataRequester                         = new JenkinsAPIRequester(urlRequester);
        sonarIssuesAPIRequester                         = new SonarQubeIssuesAPIRequester(urlRequester);
        sonarDuplicationsAPIRequester                   = new SonarQubeDuplicationsAPIRequester(urlRequester);
    }

    private void checkIfSupportedSCMIsActive(MavenModuleSet project) throws SupportedSCMNotFoundException {
        SCM scm = project.getScm();
        if(!(scm instanceof GitSCM) && !(scm instanceof SubversionSCM)) {
            throw new SupportedSCMNotFoundException("Could not find the Git or SVN source control manager");
        }
    }

    private void checkIfSonarQubePluginIsEnabled(MavenModuleSet project) throws SonarPluginNotFoundException {
        boolean preBuildersListHasSonarPlugin = false;
        boolean postBuildersListHasSonarPlugin = false;

        for (Builder builder : project.getPrebuilders()) {
            if(builder instanceof SonarRunnerBuilder){
                preBuildersListHasSonarPlugin = true;
                break;
            }
        }

        if(!preBuildersListHasSonarPlugin) {
            for (Builder builder : project.getPostbuilders()) {
                if (builder instanceof SonarRunnerBuilder) {
                    postBuildersListHasSonarPlugin = true;
                    break;
                }
            }
        }

        if(!preBuildersListHasSonarPlugin && !postBuildersListHasSonarPlugin){
            throw new SonarPluginNotFoundException("Could not find the SonarQube plugin, please make sure you activated it as a pre-build or post-build plugin");
        }
    }

    private void getJenkinsDataFromAPI(String jenkinsBaseURL, String projectURL, APIRequester<JenkinsAPIData> jenkinsAPIDataRequester) throws NoChangeSetFoundException {
        String apiURL = jenkinsBaseURL + projectURL + "api/json";
        jenkinsAPIData = jenkinsAPIDataRequester.getObjectFromAPI(apiURL, null, null);

        if (jenkinsAPIData.getChangeSet().getItems().isEmpty()) {
            throw new NoChangeSetFoundException("Could not find new changes.");
        }

        // Gets the author in the format of <JenkinsBaseURL>/user/<author> and splits the string, since we are only interested in the last part.
        String[] authorParts    = jenkinsAPIData.getChangeSet().getItems().get(0).getAuthor().getAbsoluteUrl().split("/");
        pushAuthor              = authorParts[authorParts.length-1];
    }

    private SonarIssues getSonarIssuesFromAPI(String url, int pageSize, APIRequester<SonarIssues> sonarIssuesAPIRequester) {
        String sonarQubeUser        = getDescriptor().getSONARQUBE_USER();
        String sonarQubePassword    = getDescriptor().getSONARQUBE_PASSWORD();

        SonarIssues sonarIssues     = sonarIssuesAPIRequester.getObjectFromAPI(url, sonarQubeUser, sonarQubePassword);

        // If we don't have all the issues yet, do additional requests and add the issues on the other pages to the SonarIssues
        if(sonarIssues.getTotal() > sonarIssues.getPs()) {
            int numOfIssue = sonarIssues.getPs();
            int issuesLeft = sonarIssues.getTotal() - numOfIssue;
            int page = 2;
            while(issuesLeft > 0) {
                String pagedUrl = String.format("%s&p=%s", url, String.valueOf(page));
                SonarIssues tempSonarIssues = sonarIssuesAPIRequester.getObjectFromAPI(pagedUrl, sonarQubeUser, sonarQubePassword);
                sonarIssues.getIssues().addAll(tempSonarIssues.getIssues());

                issuesLeft = numOfIssue - (page*pageSize);
                page++;
            }
        }

        return sonarIssues;
    }

    private void getNewSonarQubeIssuesFromAPI(String date, APIRequester<SonarIssues> sonarIssuesAPIRequester) {
        int pageSize = 500; // SonarQube supports a pagesize of max 500

        try {
            String url = String.format("%s/api/issues/search?componentKeys=%s&resolved=false&createdAfter=%s&ps=%s",
                    getDescriptor().getSONARQUBE_URL(),
                    URLEncoder.encode(getSONARQUBE_PROJECT_KEY(), "UTF-8"),
                    URLEncoder.encode(date, "UTF-8"),
                    String.valueOf(pageSize));

            this.newSonarIssues = getSonarIssuesFromAPI(url, pageSize, sonarIssuesAPIRequester);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void getFixedSonarIssuesObjectFromAPI(Date fixedAfter, APIRequester<SonarIssues> sonarIssuesAPIRequester) {
        int pageSize = 500; // SonarQube supports a pagesize of max 500

        try {
            String url = String.format("%s/api/issues/search?componentKeys=%s&resolved=true&s=UPDATE_DATE&asc=false&ps=%s",
                    getDescriptor().getSONARQUBE_URL(),
                    URLEncoder.encode(getSONARQUBE_PROJECT_KEY(), "UTF-8"),
                    String.valueOf(pageSize));

            SonarIssues fixedSonarIssues = getSonarIssuesFromAPI(url, pageSize, sonarIssuesAPIRequester);

            // Since the SonarQube API does not have the possibility to only get the fixed issues after a given date,
            // we need to get all the fixed issues and remove the ones that are not fixed after the given date.
            DateConverter dateConverter = new SonarQubeDateConverter();
            List<SonarIssues.Issue> issuesToRemoveFromFixedIssuesObject =
                    fixedSonarIssues.getIssues().stream()
                            .filter(issue -> {
                                try {
                                    Date issueCloseDate = dateConverter.convertStringToDate(issue.getCloseDate());
                                    return issueCloseDate.before(fixedAfter);
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                    return false;
                                }
                            }).collect(Collectors.toList());
            fixedSonarIssues.getIssues().removeAll(issuesToRemoveFromFixedIssuesObject);

            this.fixedSonarIssues = fixedSonarIssues;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void getSonarDuplicationsFromNewIssues(SonarIssues newIssuesObject, APIRequester<SonarDuplications> sonarDuplicationsAPIRequester){
        String sonarQubeUser        = getDescriptor().getSONARQUBE_USER();
        String sonarQubePassword    = getDescriptor().getSONARQUBE_PASSWORD();

        // For each of the issues containing the "common-java:DuplicatedBlocks" rule, request the SonarQube duplications API
        sonarDuplications = newIssuesObject.getIssues().parallelStream()
                .filter(issue -> "common-java:DuplicatedBlocks".equals(issue.getRule()))
                .map(issue -> {
                    String url = String.format("%s/api/duplications/show?key=%s",
                            getDescriptor().getSONARQUBE_URL(),
                            issue.getComponent());

                    return sonarDuplicationsAPIRequester.getObjectFromAPI(url, sonarQubeUser, sonarQubePassword);
                }).collect(Collectors.toList());
    }

    private void setupServerJsonObject(Run<?, ?> build,
                                       TaskListener listener,
                                       ModelConverter<SonarIssues.Issue, ServerJsonObject.Issue> sonarIssueToServerIssueConverter,
                                       ModelConverter<JenkinsAPIData.ChangeSet.Item, ServerJsonObject.Item> jenkinsItemToServerItemConverter,
                                       ModelConverter<SonarDuplications, ServerJsonObject.Duplication> sonarDuplicationToServerDuplicationConverter) {
        serverJsonObject = new ServerJsonObject();
        serverJsonObject.setResult(build.getResult().toString());
        serverJsonObject.setTimestamp(new Date().getTime());
        serverJsonObject.setAuthor(pushAuthor);

        listener.getLogger().println("[INFO] Adding changes found in the Jenkins API");
        List<ServerJsonObject.Item> items = jenkinsAPIData.getChangeSet().getItems().stream()
                .map(jenkinsItemToServerItemConverter::convert)
                .collect(Collectors.toList());
        serverJsonObject.setItems(items);

        if(!newSonarIssues.getIssues().isEmpty())
            listener.getLogger().println("[INFO] Adding the new issues found in the SonarQube API");

        List<ServerJsonObject.Issue> newIssues = newSonarIssues.getIssues().stream()
                .map(sonarIssueToServerIssueConverter::convert)
                .collect(Collectors.toList());
        serverJsonObject.setIssues(newIssues);

        if(!fixedSonarIssues.getIssues().isEmpty()) {
            listener.getLogger().println("[INFO] Adding the fixed issues found in the SonarQube API");

            List<ServerJsonObject.Issue> fixedIssues = fixedSonarIssues.getIssues().stream()
                    .map(sonarIssueToServerIssueConverter::convert)
                    .collect(Collectors.toList());
            if(serverJsonObject.getIssues() != null)
                serverJsonObject.getIssues().addAll(fixedIssues);
            else
                serverJsonObject.setIssues(fixedIssues);
        }

        if(!sonarDuplications.isEmpty())
            listener.getLogger().println("[INFO] Adding the duplications found in the SonarQube API");

        List<ServerJsonObject.Duplication> duplications = sonarDuplications.stream()
                .map(sonarDuplicationToServerDuplicationConverter::convert)
                .collect(Collectors.toList());
        serverJsonObject.setDuplications(duplications);
    }

    private void sendJsonObjectToRuleEngine(ServerJsonObject jsonObject, PostURLRequester postURLRequester) throws CouldNotRequestURLException, TokenNotFoundException, KnownInternalServerErrorException, DatabaseOfflineException {
        Gson gson = new Gson();
        try {
            String json = gson.toJson(jsonObject);
            String url = String.format("%s/projects/%s/build",
                    RULE_ENGINE_LOCATION,
                    URLEncoder.encode(getTOKEN(), "UTF-8"));

            Response response = postURLRequester.postJsonToUrl(json, url, null);

            // Check if the request was received corectly
            if(response.getStatusCode() == 404) {
                throw new TokenNotFoundException("The given token could not be found in the database, please check if it is typed right.");
            } else if(response.getStatusCode() == 503) {
                throw new DatabaseOfflineException("Database is offline");
            } /*else if(response.getStatusCode() != 200) {
                throw new KnownInternalServerErrorException("Error when parsing report");
            }*/
        } catch (DatabaseOfflineException | TokenNotFoundException | KnownInternalServerErrorException e) {
            throw e;
        } catch (UnsupportedEncodingException e) {
            throw new CouldNotRequestURLException("Could not request rule engine URL: could not urlencode the token");
        } catch (Exception e){
            throw new CouldNotRequestURLException("Could not request rule engine URL");
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
            if (value.length() < 12)
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

