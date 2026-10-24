// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Result;
import hudson.tools.ToolProperty;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Drives the real {@code oa} CLI against a real OpenArtifact server. Skipped unless {@code OA_IT_SERVER} is set;
 * {@code src/test/integration/run.sh} provisions everything and sets the variables:
 * {@code OA_IT_SERVER}, {@code OA_IT_TOKEN} (a non-admin deploy token), {@code OA_IT_OA_BIN},
 * {@code OA_IT_REPO_DEV}, {@code OA_IT_REPO_PROD}, {@code OA_IT_PIPELINE}.
 */
@WithJenkins
@EnabledIfEnvironmentVariable(named = "OA_IT_SERVER", matches = ".+")
class OaIntegrationTest {

    private static final String CREDENTIALS_ID = "it-token";

    private JenkinsRule j;

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set; use src/test/integration/run.sh");
        }
        return value;
    }

    @BeforeEach
    void configure(JenkinsRule rule) throws Exception {
        j = rule;
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, CREDENTIALS_ID, "it token", Secret.fromString(env("OA_IT_TOKEN"))));
        SystemCredentialsProvider.getInstance().save();
        OpenArtifactServer server = new OpenArtifactServer("it", env("OA_IT_SERVER"));
        server.setCredentialsId(CREDENTIALS_ID);
        OpenArtifactGlobalConfiguration config = OpenArtifactGlobalConfiguration.get();
        config.setServers(List.of(server));
        config.setDefaultServerId("it");
        Jenkins.get()
                .getDescriptorByType(OaInstallation.DescriptorImpl.class)
                .setInstallations(new OaInstallation("real", env("OA_IT_OA_BIN"), List.<ToolProperty<?>>of()));
        config.setDefaultInstallation("real");
    }

    @Test
    void uploadBundleAndPromoteAgainstARealServer() throws Exception {
        String bundleVersion = "it-" + System.nanoTime();
        WorkflowJob job = j.createProject(WorkflowJob.class, "shop");
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'dist/app.tar.gz', text: 'app build'
                  writeFile file: 'dist/docs.txt', text: 'docs build'
                  writeFile file: 'dist/sub/extra.bin', text: 'extra build'
                  withEnv(['GIT_COMMIT=1a2b3c4d5e6f']) {
                    def up = oaUpload(repo: '%s', files: 'dist/**', targetPath: "builds/${env.BUILD_NUMBER}/",
                                      properties: 'team=core')
                    echo "UPLOADED=${up.uploaded.size()}"
                  }
                  def b = oaBundle(name: 'shop', version: '%s', fromBuild: true, pipeline: '%s',
                                   description: 'jenkins integration test')
                  echo "BUNDLE_STATE=${b.state}"
                  def p = oaPromote(bundle: 'shop', version: '%s', stage: 'prod', notes: 'from jenkins')
                  echo "PROMOTED_STAGE=${p.promoted.stage} COPIED=${p.promoted.copied} TARGET=${p.promoted.target_repo}"
                }
                """.formatted(env("OA_IT_REPO_DEV"), bundleVersion, env("OA_IT_PIPELINE"), bundleVersion), true));

        WorkflowRun build = j.buildAndAssertSuccess(job);

        j.assertLogContains("UPLOADED=3", build);
        j.assertLogContains("BUNDLE_STATE=sealed", build);
        j.assertLogContains("PROMOTED_STAGE=prod COPIED=3 TARGET=" + env("OA_IT_REPO_PROD"), build);
        String log = org.apache.commons.io.IOUtils.toString(build.getLogInputStream(), StandardCharsets.UTF_8);
        assertFalse(log.contains(env("OA_IT_TOKEN")), "token leaked into the build log");

        OaBuildAction action = build.getAction(OaBuildAction.class);
        assertNotNull(action);
        assertEquals(3, action.getUploadedCount());
        OaBuildAction.Upload upload = action.getUploads().get(0);
        assertEquals(env("OA_IT_REPO_DEV"), upload.getRepo());
        for (OaBuildAction.Artifact artifact : upload.getArtifacts()) {
            assertTrue(artifact.getPath().startsWith("builds/1/dist/"), artifact.getPath());
            assertEquals(64, artifact.getSha256().length());
            assertTrue(artifact.getSize() > 0);
        }
        assertEquals(env("OA_IT_SERVER") + "/bundles/shop/" + bundleVersion, action.getPromotions().get(0).getLink());
        assertEquals(3, action.getPromotions().get(0).getItems());

        // The promoted files really are in the target repository.
        String packages = oa("repo", "packages", env("OA_IT_REPO_PROD"));
        assertTrue(packages.contains("app.tar.gz"), packages);
    }

    @Test
    void badTokenFailsClearlyWithoutLeakingIt() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().clear();
        String bad = "oa_dep_definitely-not-a-valid-token";
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new StringCredentialsImpl(CredentialsScope.GLOBAL, CREDENTIALS_ID, "bad", Secret.fromString(bad)));
        WorkflowJob job = j.createProject(WorkflowJob.class, "badtoken");
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  oaUpload(repo: '%s', files: 'a.txt')
                }
                """.formatted(env("OA_IT_REPO_DEV")), true));

        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));

        j.assertLogContains("oa upload failed with exit code 1", build);
        String log = org.apache.commons.io.IOUtils.toString(build.getLogInputStream(), StandardCharsets.UTF_8);
        assertFalse(log.contains(bad), "token leaked into the build log");
        assertTrue(build.getAction(OaBuildAction.class) == null);
    }

    /** Runs the real oa with the CI token and returns stdout. */
    private static String oa(String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add(env("OA_IT_OA_BIN"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("OA_SERVER", env("OA_IT_SERVER"));
        environment.put("OA_TOKEN", env("OA_IT_TOKEN"));
        Process process = builder.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        process.getInputStream().transferTo(out);
        int exit = process.waitFor();
        String text = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit, text);
        return text;
    }
}
