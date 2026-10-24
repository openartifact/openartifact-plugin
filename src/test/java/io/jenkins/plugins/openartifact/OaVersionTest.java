// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OaVersionTest {

    @Test
    void parsesReleaseNumbers() {
        assertEquals(new OaVersion(0, 10, 0), OaVersion.parse("0.10.0"));
        assertEquals(new OaVersion(1, 2, 3), OaVersion.parse("v1.2.3"));
        assertEquals(new OaVersion(0, 10, 1), OaVersion.parse(" 0.10.1-rc1 "));
    }

    @Test
    void rejectsNonReleaseStrings() {
        assertNull(OaVersion.parse("dev"));
        assertNull(OaVersion.parse(""));
        assertNull(OaVersion.parse(null));
        assertNull(OaVersion.parse("1.2"));
    }

    @Test
    void ordersNumerically() {
        assertTrue(OaVersion.parse("0.9.9").compareTo(OaVersion.parse("0.10.0")) < 0);
        assertTrue(OaVersion.parse("0.10.0").compareTo(OaVersion.parse("0.10.0")) == 0);
        assertTrue(OaVersion.parse("1.0.0").compareTo(OaVersion.parse("0.99.99")) > 0);
        assertTrue(OaVersion.parse("0.10.2").compareTo(OaVersion.parse("0.10.10")) < 0);
    }

    @Test
    void readsReportedVersionFromJsonOrText() {
        assertEquals("0.10.0", OaRunner.reportedVersion("{\"version\":\"0.10.0\",\"commit\":\"abc\"}"));
        assertEquals("0.10.0", OaRunner.reportedVersion("oa version 0.10.0\n"));
        assertNull(OaRunner.reportedVersion("something else"));
    }
}
