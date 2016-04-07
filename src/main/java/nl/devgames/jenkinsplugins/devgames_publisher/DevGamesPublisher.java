package nl.devgames.jenkinsplugins.devgames_publisher;
import com.mashape.unirest.http.Unirest;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;

public class DevGamesPublisher extends Publisher implements SimpleBuildStep {

    private final String token;

    @DataBoundConstructor
    public DevGamesPublisher(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {

        listener.getLogger().println("Hello DevGames plugin!");

        String url = build.getUrl();
        String baseURL = null;
        try {
            baseURL = build.getEnvironment(listener).get("JENKINS_URL");
        } catch (IOException e) {
            build.setResult(Result.FAILURE);
            e.printStackTrace();
        } catch (InterruptedException e) {
            build.setResult(Result.FAILURE);
            e.printStackTrace();
        }

        if(baseURL != null) {
            String API_URL = baseURL + url + "/api";
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
            return true;
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

