// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OaBuildActionTest {

    private static final String SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private FreeStyleBuild buildWithRecords(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("web");
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        OaBuildAction action = OaBuildAction.of(build);
        action.addUpload(
                "https://oa.example.test",
                "releases",
                List.of(
                        new OaBuildAction.Artifact("app/app-1.0.jar", SHA, 2_500_000L, "https://oa.example.test/releases/app/app-1.0.jar"),
                        new OaBuildAction.Artifact("app/app-1.0.pom", SHA, 900L, "javascript:alert(1)")));
        action.addBundle(new OaBuildAction.Bundle("https://oa.example.test", "app", "1.0", "sealed"));
        action.addPromotion(new OaBuildAction.Promotion("https://oa.example.test", "app", "1.0", "prod", "releases", 2));
        build.save();
        return build;
    }

    @Test
    void buildPageShowsBadgesAndArtifactSummary(JenkinsRule j) throws Exception {
        FreeStyleBuild build = buildWithRecords(j);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(build);
            String html = page.asXml();
            assertTrue(html.contains("2 \u2192 releases"), "upload badge text");
            assertTrue(html.contains("app@1.0 \u2192 prod"), "promotion badge text");
            assertTrue(html.contains("openartifact-mark-color.svg"), "mark icon");
            assertTrue(html.contains(SHA.substring(0, 12)), "short sha");
            assertTrue(html.contains(SHA), "full sha in title");
            assertTrue(html.contains("2.4 MiB"), "size");
            assertTrue(page.getAnchors().stream().anyMatch(a -> "https://oa.example.test/repos/releases".equals(a.getHrefAttribute())));
            assertTrue(page.getAnchors().stream().anyMatch(a -> "https://oa.example.test/bundles/app/1.0".equals(a.getHrefAttribute())));
            assertTrue(page.getAnchors().stream().anyMatch(a -> "https://oa.example.test/releases/app/app-1.0.jar".equals(a.getHrefAttribute())));
            assertTrue(page.getAnchors().stream().noneMatch(a -> a.getHrefAttribute().startsWith("javascript:")),
                    "unsafe link must not be rendered");
        }
    }

    @Test
    void historyRowShowsBadges(JenkinsRule j) throws Exception {
        FreeStyleBuild build = buildWithRecords(j);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(build.getParent());
            List<DomElement> badges = page.getByXPath("//a[contains(@class,'oa-badge')]");
            assertEquals(2, badges.size(), page.asXml());
            assertEquals("https://oa.example.test/repos/releases", ((HtmlAnchor) badges.get(0)).getHrefAttribute());
            assertEquals("https://oa.example.test/bundles/app/1.0", ((HtmlAnchor) badges.get(1)).getHrefAttribute());
        }
    }

    @Test
    void buildWithoutRecordsRendersNothing(JenkinsRule j) throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            assertTrue(wc.getPage(build).getByXPath("//*[contains(@class,'oa-summary')]").isEmpty());
        }
    }

    @Test
    void uploadsToTheSameRepoAreMerged(JenkinsRule j) throws Exception {
        FreeStyleBuild build = j.buildAndAssertSuccess(j.createFreeStyleProject());
        OaBuildAction action = OaBuildAction.of(build);
        action.addUpload("https://oa.example.test", "r", List.of(new OaBuildAction.Artifact("a", SHA, 1, null)));
        action.addUpload("https://oa.example.test", "r", List.of(new OaBuildAction.Artifact("b", SHA, 1, null)));
        assertEquals(1, action.getUploads().size());
        assertEquals(2, action.getUploadedCount());
    }

    @Test
    void persistsAcrossReload(JenkinsRule j) throws Exception {
        FreeStyleBuild build = buildWithRecords(j);
        j.jenkins.reload();
        FreeStyleBuild reloaded = j.jenkins.getItemByFullName("web", FreeStyleProject.class).getBuildByNumber(build.getNumber());
        OaBuildAction action = reloaded.getAction(OaBuildAction.class);
        assertEquals(2, action.getUploadedCount());
        assertEquals("prod", action.getPromotions().get(0).getStage());
        assertEquals(reloaded, action.getRun());
    }

    @Test
    void safeLinkAcceptsOnlyHttp() {
        assertEquals("https://x.test/a", OaBuildAction.safeLink(" https://x.test/a "));
        assertEquals("http://x.test", OaBuildAction.safeLink("http://x.test"));
        assertNull(OaBuildAction.safeLink("javascript:alert(1)"));
        assertNull(OaBuildAction.safeLink("data:text/html,x"));
        assertNull(OaBuildAction.safeLink(""));
        assertNull(OaBuildAction.safeLink(null));
        assertNull(OaBuildAction.safeLink("http://bad url"));
    }

    @Test
    void humanSizeUsesBinaryUnits() {
        assertEquals("0 B", OaBuildAction.humanSize(0));
        assertEquals("1023 B", OaBuildAction.humanSize(1023));
        assertEquals("1.0 KiB", OaBuildAction.humanSize(1024));
        assertEquals("2.4 MiB", OaBuildAction.humanSize(2_500_000));
    }
}
