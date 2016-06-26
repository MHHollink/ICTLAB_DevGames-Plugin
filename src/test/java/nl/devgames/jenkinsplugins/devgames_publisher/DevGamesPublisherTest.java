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
    public void testDevGamesPublisherIsApplicableForMavenProject() throws Exception {
        MavenModuleSet project = j.jenkins.createProject(MavenModuleSet.class, "p");
        DevGamesPublisher devGamesPublisher = new DevGamesPublisher("","");

        assertTrue(devGamesPublisher.getDescriptor().isApplicable(project.getClass()));
    }

    @Test
    public void testDevGamesPublisherIsNotApplicableForFreestyleProject() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("freestyle project");
        DevGamesPublisher devGamesPublisher = new DevGamesPublisher("","");

        assertFalse(devGamesPublisher.getDescriptor().isApplicable(project.getClass()));
    }
}