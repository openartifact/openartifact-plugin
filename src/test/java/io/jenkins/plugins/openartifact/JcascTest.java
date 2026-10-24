// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class JcascTest {

    @Test
    @ConfiguredWithCode("jcasc.yaml")
    void loadsServersFromYaml(JenkinsConfiguredWithCodeRule j) {
        OpenArtifactGlobalConfiguration config = OpenArtifactGlobalConfiguration.get();
        assertEquals(2, config.getServers().size());
        assertEquals("prod", config.getServers().get(0).getId());
        assertEquals("https://artifacts.example.com", config.getServers().get(0).getUrl());
        assertEquals("oa-prod-token", config.getServers().get(0).getCredentialsId());
        assertEquals("prod", config.getDefaultServerId());
        assertEquals("oa", config.getDefaultInstallation());
        OaInstallation[] installations = OaInstallation.installations();
        assertEquals(1, installations.length);
        assertEquals("oa", installations[0].getName());
        OaInstaller installer = (OaInstaller) installations[0]
                .getProperties()
                .get(hudson.tools.InstallSourceProperty.class)
                .installers
                .get(0);
        assertEquals("0.10.0", installer.getVersion());
    }
}
