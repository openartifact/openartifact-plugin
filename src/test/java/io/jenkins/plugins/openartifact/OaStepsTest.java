// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@DisabledOnOs(OS.WINDOWS)
class OaStepsTest {

    private JenkinsRule j;

    @TempDir
    private Path oaDir;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        OaTestSupport.installFakeOa(oaDir);
        OaTestSupport.configure(oaDir);
    }

    private WorkflowRun run(String script) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "app");
        job.setDefinition(new CpsFlowDefinition(script, true));
        return j.buildAndAssertSuccess(job);
    }

    @Test
    void uploadReturnsResultAndRecordsBadge() throws Exception {
        WorkflowRun build = run("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  writeFile file: 'b.txt', text: 'b'
                  def r = oaUpload(repo: 'releases', files: 'a.txt\\nb.txt', targetPath: 'app/1.0',
                                   flat: true, properties: 'team=infra\\n# comment\\nchannel=stable')
                  echo "count=${r.uploaded.size()} first=${r.uploaded[0].path} repo=${r.uploaded[0].repo}"
                }
                """);
        j.assertLogContains("count=2 first=a.txt repo=releases", build);

        String call = OaTestSupport.calls(oaDir).stream()
                .filter(c -> c.contains(" upload "))
                .findFirst()
                .orElseThrow();
        assertTrue(call.startsWith("--format json upload releases a.txt b.txt"), call);
        assertTrue(call.contains("--target-path app/1.0"), call);
        assertTrue(call.contains("--flat"), call);
        assertTrue(call.contains("--property team=infra"), call);
        assertTrue(call.contains("--property channel=stable"), call);
        assertFalse(call.contains("# comment"), call);
        assertTrue(call.contains("--build-name app"), call);
        assertTrue(call.contains("--build-number 1"), call);
        assertTrue(call.contains("--build-url " + j.getURL() + "job/app/1/"), call);

        OaBuildAction action = build.getAction(OaBuildAction.class);
        assertNotNull(action);
        assertEquals(2, action.getUploadedCount());
        assertEquals("releases", action.getUploads().get(0).getRepo());
        assertEquals(OaTestSupport.SERVER_URL + "/repos/releases", action.getUploads().get(0).getLink());
    }

    @Test
    void uploadPassesVcsRevisionFromGitCommit() throws Exception {
        WorkflowRun build = run("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  withEnv(['GIT_COMMIT=0123abcd']) {
                    oaUpload(repo: 'releases', files: 'a.txt')
                  }
                }
                """);
        String call = OaTestSupport.calls(oaDir).stream()
                .filter(c -> c.contains(" upload "))
                .findFirst()
                .orElseThrow();
        assertTrue(call.contains("--vcs-revision 0123abcd"), call);
        assertNotNull(build.getAction(OaBuildAction.class));
    }

    @Test
    void tokenIsInEnvironmentOnlyAndMaskedInLog() throws Exception {
        Files.writeString(oaDir.resolve("leak"), "");
        WorkflowRun build = run("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  oaUpload(repo: 'releases', files: 'a.txt')
                }
                """);
        for (String call : OaTestSupport.calls(oaDir)) {
            assertFalse(call.contains(OaTestSupport.TOKEN), "token on command line: " + call);
        }
        assertTrue(OaTestSupport.envLog(oaDir).contains("OA_TOKEN=" + OaTestSupport.TOKEN));
        assertTrue(OaTestSupport.envLog(oaDir).contains("OA_SERVER=" + OaTestSupport.SERVER_URL));
        String log = org.apache.commons.io.IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertFalse(log.contains(OaTestSupport.TOKEN), log);
        assertTrue(log.contains("token is ****"), log);
    }

    @Test
    void bundleFromBuildCreatesAddsAndSeals() throws Exception {
        WorkflowRun build = run("""
                node {
                  def r = oaBundle(name: 'app', version: '1.0', fromBuild: true, description: 'first',
                                   artifacts: 'extra:docs/readme.md\\nextra:docs/notes.md')
                  echo "state=${r.state} bundle=${r.bundle}"
                }
                """);
        j.assertLogContains("state=sealed bundle=app", build);
        List<String> calls = OaTestSupport.calls(oaDir).stream()
                .filter(c -> c.contains(" bundle "))
                .toList();
        assertEquals(4, calls.size(), calls.toString());
        assertEquals("--format json bundle create app 1.0 --description first", calls.get(0));
        assertEquals("--format json bundle add app 1.0 --from-build app/1", calls.get(1));
        assertEquals(
                "--format json bundle add app 1.0 --repo extra --path docs/readme.md --path docs/notes.md",
                calls.get(2));
        assertEquals("--format json bundle seal app 1.0", calls.get(3));
        OaBuildAction action = build.getAction(OaBuildAction.class);
        assertNotNull(action);
        assertEquals(OaTestSupport.SERVER_URL + "/bundles/app/1.0", action.getBundles().get(0).getLink());
    }

    @Test
    void bundleCanSkipCreateAndSeal() throws Exception {
        run("""
                node {
                  oaBundle(name: 'app', version: '1.0', fromBuild: true, create: false, seal: false)
                }
                """);
        List<String> calls = OaTestSupport.calls(oaDir).stream()
                .filter(c -> c.contains(" bundle "))
                .toList();
        assertEquals(List.of("--format json bundle add app 1.0 --from-build app/1"), calls);
    }

    @Test
    void promoteReturnsResultAndRecordsBadge() throws Exception {
        WorkflowRun build = run("""
                node {
                  def r = oaPromote(bundle: 'app', version: '1.0', stage: 'prod', targetRepo: 'releases', notes: 'go live')
                  echo "stage=${r.promoted.stage} items=${r.promoted.items}"
                }
                """);
        j.assertLogContains("stage=prod items=2", build);
        String call = OaTestSupport.calls(oaDir).stream()
                .filter(c -> c.contains(" promote "))
                .findFirst()
                .orElseThrow();
        assertEquals("--format json promote app 1.0 --stage prod --target-repo releases --notes go live", call);
        OaBuildAction.Promotion promotion = build.getAction(OaBuildAction.class).getPromotions().get(0);
        assertEquals("prod", promotion.getStage());
        assertEquals(2, promotion.getItems());
        assertEquals(OaTestSupport.SERVER_URL + "/bundles/app/1.0", promotion.getLink());
    }

    @Test
    void failingOaFailsTheBuildWithItsMessage() throws Exception {
        Files.writeString(oaDir.resolve("fail"), "");
        WorkflowJob job = j.createProject(WorkflowJob.class, "failing");
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  oaUpload(repo: 'releases', files: 'a.txt')
                }
                """, true));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("oa upload failed with exit code 1: Error: server said no", build);
        assertTrue(build.getAction(OaBuildAction.class) == null);
    }

    @Test
    void partialUploadKeepsBadgeForStoredFilesAndReportsOaError() throws Exception {
        Files.writeString(oaDir.resolve("partial"), "");
        WorkflowJob job = j.createProject(WorkflowJob.class, "partial");
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  oaUpload(repo: 'releases', files: 'a.txt')
                }
                """, true));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("oa upload failed with exit code 1: disk full", build);
        assertEquals(1, build.getAction(OaBuildAction.class).getUploadedCount());
    }

    @Test
    void oldOaIsRejected() throws Exception {
        Files.writeString(oaDir.resolve("version"), "{\"version\":\"0.9.0\"}");
        WorkflowJob job = j.createProject(WorkflowJob.class, "old");
        job.setDefinition(new CpsFlowDefinition("""
                node { oaPromote(bundle: 'app', version: '1.0', stage: 'prod') }
                """, true));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("oa 0.9.0 is too old: this plugin needs oa 0.10.0 or newer", build);
    }

    @Test
    void unknownServerIsReported() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "noserver");
        job.setDefinition(new CpsFlowDefinition("""
                node { oaPromote(server: 'nope', bundle: 'app', version: '1.0', stage: 'prod') }
                """, true));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("No OpenArtifact server with id 'nope'", build);
    }

    @Test
    void invalidPropertyIsReported() throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "badprop");
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'a.txt', text: 'a'
                  oaUpload(repo: 'releases', files: 'a.txt', properties: 'oops')
                }
                """, true));
        WorkflowRun build = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("Property 'oops' must look like key=value", build);
    }

    @Test
    void freestyleBuildStepAndPostBuildAction() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hi > a.txt"));
        project.getBuildersList().add(new OaUploadBuilder("releases", "a.txt"));
        OaBundleBuilder bundle = new OaBundleBuilder("app", "2.0");
        bundle.setFromBuild(true);
        project.getBuildersList().add(bundle);
        project.getPublishersList().add(new OaPromoteRecorder("app", "2.0", "staging"));
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        OaBuildAction action = build.getAction(OaBuildAction.class);
        assertNotNull(action);
        assertEquals(1, action.getUploadedCount());
        assertEquals("staging", action.getPromotions().get(0).getStage());
        assertEquals("app", action.getBundles().get(0).getName());
    }
}
