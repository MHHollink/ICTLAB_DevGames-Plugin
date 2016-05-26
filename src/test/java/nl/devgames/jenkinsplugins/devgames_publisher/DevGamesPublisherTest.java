package nl.devgames.jenkinsplugins.devgames_publisher;

import hudson.maven.MavenModuleSet;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.sonar.SonarRunnerBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class DevGamesPublisherTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void first() throws Exception {
        MavenModuleSet project = j.jenkins.createProject(MavenModuleSet.class, "p");
        DevGamesPublisher devGamesPublisher = new DevGamesPublisher("","");
//        SonarRunnerBuilder sonarRunnerBuilder = new SonarRunnerBuilder()

        assertTrue(devGamesPublisher.getDescriptor().isApplicable(project.getClass()));
    }


/*    @Test public void first() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getDisplayName() + " completed");
        // TODO: change this to use HtmlUnit
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, contains("+ echo hello"));
    }*/
}