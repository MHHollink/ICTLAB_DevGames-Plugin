package nl.devgames.jenkinsplugins.devgames_publisher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.JenkinsJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.ServerJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube.SonarActivitiesJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube.SonarDuplicationsJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube.SonarIssuesJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube.deserializers.SonarDuplicationJsonObjectDeserializer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.Interceptor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DevGamesPublisher extends Publisher implements SimpleBuildStep {

    private final String token;
    private final String sqPK;

    @DataBoundConstructor
    public DevGamesPublisher(String token, String sqPK) {
        this.token = token;
        this.sqPK = sqPK;
    }

    public String getToken() {
        return token;
    }

    public String getSonarQubeProjectKey() {
        return sqPK;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {

        try {
            String url                  = build.getUrl();
            String baseURL              = build.getEnvironment(listener).get("JENKINS_URL");
            String POM_GROUPID          = build.getEnvironment(listener).get("POM_GROUPID");
            String POM_ARTIFACTID       = build.getEnvironment(listener).get("POM_ARTIFACTID");
            String sonarQubeProjectKey  = POM_GROUPID + ":" + POM_ARTIFACTID;

            GsonBuilder gsonBuilder     = new GsonBuilder()
                                                .registerTypeAdapter(SonarDuplicationsJsonObject.class, new SonarDuplicationJsonObjectDeserializer());
            Gson gson                   = gsonBuilder.create();

            if(baseURL != null) {
                /*
                Get the build and SCM information from Jenkins
                */
                String API_URL = baseURL + url + "api/json";
                HttpResponse<JsonNode> jsonResponse = Unirest.get(API_URL)
                        .header("accept", "application/json")
                        .asJson();

                String json = jsonResponse.getBody().toString();
                JenkinsJsonObject jenkinsJsonObject = gson.fromJson(json, JenkinsJsonObject.class);

                /*
                Get the information about the last SonarQube analysis
                */
                jsonResponse = Unirest.get("http://localhost:9000/api/ce/activity?componentQuery=" + sonarQubeProjectKey + "&onlyCurrents=true")
                        .basicAuth("admin","admin")
                        .header("accept", "application/json")
                        .asJson();

                json = jsonResponse.getBody().toString();
                SonarActivitiesJsonObject sonarActivitiesJsonObject = gson.fromJson(json, SonarActivitiesJsonObject.class);

                /*
                Get the new issues from SonarQube
                */
                String urlEncodedDate = URLEncoder.encode(sonarActivitiesJsonObject.getTasks().get(0).getStartedAt(),"UTF-8");

                jsonResponse = Unirest.get("http://localhost:9000/api/issues/search?componentKeys=" + sonarQubeProjectKey + "&resolved=false&createdAfter=" + urlEncodedDate)
                        .basicAuth("admin","admin")
                        .header("accept", "application/json")
                        .asJson();

                json = jsonResponse.getBody().toString();
                SonarIssuesJsonObject newSonarIssuesJsonObject = gson.fromJson(json, SonarIssuesJsonObject.class);

                /*
                Get issues which are marked as FIXED since this analysis
                 */
                jsonResponse = Unirest.get("http://localhost:9000/api/issues/search?componentKeys=" + sonarQubeProjectKey + "&resolved=true&ps=500&s=UPDATE_DATE&asc=false")
                        .basicAuth("admin","admin")
                        .header("accept", "application/json")
                        .asJson();

                json = jsonResponse.getBody().toString();
                SonarIssuesJsonObject fixedSonarIssuesJsonObject = gson.fromJson(json, SonarIssuesJsonObject.class);

                List<SonarIssuesJsonObject.Issues> fixedIssues = new ArrayList<>();

                SimpleDateFormat simpleDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                String analysisDateString = sonarActivitiesJsonObject.getTasks().get(0).getStartedAt();
                Date analysisDate = simpleDateFormatter.parse(analysisDateString);

                for(SonarIssuesJsonObject.Issues issue : fixedSonarIssuesJsonObject.getIssues()){
                    Date issueFixedDate = simpleDateFormatter.parse(issue.getCloseDate());

                    if (issueFixedDate.after(analysisDate)) {
                        fixedIssues.add(issue);
                    } else {
                        break;
                    }
                }

                /*
                Get the duplications
                 */
                List<SonarDuplicationsJsonObject> duplicationsList = new ArrayList<>();
                List<String> duplicatedFiles = new ArrayList<>();
                for (SonarIssuesJsonObject.Issues issue : newSonarIssuesJsonObject.getIssues()) {
                    if (issue.getRule().equals("common-java:DuplicatedBlocks")) {
                        duplicatedFiles.add(issue.getComponent());
                    }
                }

                for (String file : duplicatedFiles) {
                    jsonResponse = Unirest.get("http://localhost:9000/api/duplications/show?key=" + file)
                            .basicAuth("admin","admin")
                            .header("accept", "application/json")
                            .asJson();

                    json = jsonResponse.getBody().toString();
                    SonarDuplicationsJsonObject sonarDuplicationsJsonObject = gson.fromJson(json, SonarDuplicationsJsonObject.class);
                    duplicationsList.add(sonarDuplicationsJsonObject);
                }

                /*
                Create an object for the json we need to return
                 */
                ServerJsonObject serverJsonObject = new ServerJsonObject();
                serverJsonObject.setResult(build.getResult().toString());
                serverJsonObject.setTimestamp(new Date().getTime());
                serverJsonObject.setAuthor(jenkinsJsonObject.getCulprits()[0].getFullName());

                /*
                Parse commits from the jenkins json object and convert them for the server json object
                 */
                List<ServerJsonObject.Item> items = new ArrayList<>();
                simpleDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
                for (JenkinsJsonObject.ChangeSet.Items item : jenkinsJsonObject.getChangeSet().getItems()) {
                    ServerJsonObject.Item newItem = serverJsonObject.new Item();
                    newItem.setCommitId(item.getCommitId());
                    newItem.setCommitMsg(item.getMsg());

                    Date commitDate = simpleDateFormatter.parse(item.getDate());
                    long timestamp = commitDate.getTime();
                    newItem.setTimestamp(timestamp);
                    items.add(newItem);
                }
                serverJsonObject.setItems(items);

                /*
                Parse the issues from SonarQube
                 */
                List<ServerJsonObject.Issue> issues = new ArrayList<>();
                simpleDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

                // newly created issues
                for (SonarIssuesJsonObject.Issues issue : newSonarIssuesJsonObject.getIssues()){
                    ServerJsonObject.Issue newIssue = serverJsonObject.new Issue();
                    newIssue.setSeverity(issue.getSeverity());
                    newIssue.setComponent(issue.getComponent());
                    newIssue.setStartLine(issue.getTextRange().getStartLine());
                    newIssue.setEndLine(issue.getTextRange().getEndLine());
                    newIssue.setStatus(issue.getStatus());
                    newIssue.setResolution(issue.getResolution());
                    newIssue.setMessage(issue.getMessage());

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
                    newIssue.setDebt(debt + "min");

                    Date issueCreationDate = simpleDateFormatter.parse(issue.getCreationDate());
                    long issueCreationTimestamp = issueCreationDate.getTime();
                    newIssue.setCreationDate(issueCreationTimestamp);
                    issues.add(newIssue);
                }

                // fixed issues
                for (SonarIssuesJsonObject.Issues issue : fixedIssues){
                    ServerJsonObject.Issue newIssue = serverJsonObject.new Issue();
                    newIssue.setSeverity(issue.getSeverity());
                    newIssue.setComponent(issue.getComponent());
                    newIssue.setStartLine(issue.getTextRange().getStartLine());
                    newIssue.setEndLine(issue.getTextRange().getEndLine());
                    newIssue.setStatus(issue.getStatus());
                    newIssue.setResolution(issue.getResolution());
                    newIssue.setMessage(issue.getMessage());

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
                    newIssue.setDebt(debt + "min");

                    Date issueCreationDate = simpleDateFormatter.parse(issue.getCreationDate());
                    long issueCreationTimestamp = issueCreationDate.getTime();
                    newIssue.setCreationDate(issueCreationTimestamp);

                    Date issueUpdateDate = simpleDateFormatter.parse(issue.getUpdateDate());
                    long issueUpdateTimestamp = issueUpdateDate.getTime();
                    newIssue.setCreationDate(issueUpdateTimestamp);

                    Date issueCloseDate = simpleDateFormatter.parse(issue.getCloseDate());
                    long issueCloseTimestamp = issueCloseDate.getTime();
                    newIssue.setCreationDate(issueCloseTimestamp);
                    issues.add(newIssue);
                }
                serverJsonObject.setIssues(issues);

                /*
                Process the duplications
                 */
                List<ServerJsonObject.Duplication> duplications = new ArrayList<>();
                for (SonarDuplicationsJsonObject fileDuplications : duplicationsList) {
                    ServerJsonObject.Duplication newDuplication = serverJsonObject.new Duplication();

                    for (SonarDuplicationsJsonObject.Duplications duplication : fileDuplications.getDuplications()) {

                        List<ServerJsonObject.Duplication.File> duplicationFiles = new ArrayList<>();
                        for (SonarDuplicationsJsonObject.Duplications.Blocks block : duplication.getBlocks()) {
                            ServerJsonObject.Duplication.File duplicationFile = newDuplication.new File();

                            duplicationFile.setBeginLine(block.getFrom());
                            duplicationFile.setSize(block.getSize());

                            // Find the file belonging to this block
                            SonarDuplicationsJsonObject.Files blockFile = null;
                            for (SonarDuplicationsJsonObject.Files file : fileDuplications.getFiles()) {
                                if (file.getRef().equals(block.get_ref())) {
                                    blockFile = file;
                                    break;
                                }
                            }

                            duplicationFile.setFile(blockFile.getName());
                            duplicationFiles.add(duplicationFile);
                        }
                        newDuplication.setFiles(duplicationFiles);
                    }
                    duplications.add(newDuplication);
                }
                serverJsonObject.setDuplications(duplications);

                listener.getLogger().println();
                listener.getLogger().println();
                listener.getLogger().println();

                String returnJson = gson.toJson(serverJsonObject);
                listener.getLogger().println(returnJson);

                listener.getLogger().println();
                listener.getLogger().println();
                listener.getLogger().println();

            } else {
                throw new NullPointerException("Jenkins base url could not be found");
            }
        } catch (IOException | InterruptedException | NullPointerException | UnirestException | ParseException e) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println("ERROR: " + e.getMessage());
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

        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            if(aClass.getName().equals("hudson.maven.MavenModuleSet")) {
                return true;
            } else {
                return false;
            }
        }

        public String getDisplayName() {
            return "Run DevGames publish task";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }
    }
}

