// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.FreeStyleProject;
import hudson.tools.ToolProperty;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ConfigurationTest {

    @Test
    void globalConfigRoundTrips(JenkinsRule j) throws Exception {
        OpenArtifactServer one = new OpenArtifactServer("prod", "https://oa.example.com/");
        one.setCredentialsId("tok");
        OpenArtifactServer two = new OpenArtifactServer("lab", "http://localhost:8080");
        OpenArtifactGlobalConfiguration config = OpenArtifactGlobalConfiguration.get();
        config.setServers(List.of(one, two));
        config.setDefaultServerId("lab");

        j.configRoundtrip();

        OpenArtifactGlobalConfiguration after = OpenArtifactGlobalConfiguration.get();
        assertEquals(2, after.getServers().size());
        assertEquals("prod", after.getServers().get(0).getId());
        assertEquals("https://oa.example.com", after.getServers().get(0).getUrl(), "trailing slash dropped");
        assertEquals("tok", after.getServers().get(0).getCredentialsId());
        assertEquals("lab", after.getServers().get(1).getId());
        assertNull(after.getServers().get(1).getCredentialsId());
        assertEquals("lab", after.getDefaultServerId());
    }

    @Test
    void serverLookupFollowsDefaultAndSingleServerRules(JenkinsRule j) throws Exception {
        OpenArtifactGlobalConfiguration config = OpenArtifactGlobalConfiguration.get();
        config.setServers(List.of(new OpenArtifactServer("only", "https://a.example")));
        assertEquals("only", config.findServer(null).getId());

        config.setServers(List.of(new OpenArtifactServer("a", "https://a.example"), new OpenArtifactServer("b", "https://b.example")));
        assertNull(config.findServer(null), "ambiguous without a default");
        config.setDefaultServerId("b");
        assertEquals("b", config.findServer("").getId());
        assertEquals("a", config.findServer("a").getId());
        assertNull(config.findServer("missing"));
    }

    @Test
    void installationRoundTrips(JenkinsRule j) throws Exception {
        OaInstaller installer = new OaInstaller(null);
        installer.setVersion("v0.10.1");
        Jenkins.get()
                .getDescriptorByType(OaInstallation.DescriptorImpl.class)
                .setInstallations(new OaInstallation("oa", "/opt/oa", List.<ToolProperty<?>>of()));
        j.configRoundtrip();
        OaInstallation[] installations = OaInstallation.installations();
        assertEquals(1, installations.length);
        assertEquals("oa", installations[0].getName());
        assertEquals("/opt/oa", installations[0].getHome());
        assertEquals("0.10.1", installer.getVersion());
    }

    @Test
    void stepFormsRoundTrip(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        OaUploadBuilder upload = new OaUploadBuilder("releases", "target/*.jar");
        upload.setTargetPath("app");
        upload.setFlat(true);
        upload.setProperties("a=b");
        project.getBuildersList().add(upload);
        OaBundleBuilder bundle = new OaBundleBuilder("app", "1.0");
        bundle.setFromBuild(true);
        bundle.setSeal(false);
        project.getBuildersList().add(bundle);
        project.getPublishersList().add(new OaPromoteRecorder("app", "1.0", "prod"));
        project.save();

        j.configRoundtrip(project);

        OaUploadBuilder u = project.getBuildersList().get(OaUploadBuilder.class);
        assertEquals("releases", u.getRepo());
        assertEquals("target/*.jar", u.getFiles());
        assertEquals("app", u.getTargetPath());
        assertEquals(true, u.isFlat());
        assertEquals("a=b", u.getProperties());
        OaBundleBuilder b = project.getBuildersList().get(OaBundleBuilder.class);
        assertEquals(true, b.isFromBuild());
        assertEquals(false, b.isSeal());
        assertEquals(true, b.isCreate());
        OaPromoteRecorder p = project.getPublishersList().get(OaPromoteRecorder.class);
        assertEquals("prod", p.getStage());
    }
}
