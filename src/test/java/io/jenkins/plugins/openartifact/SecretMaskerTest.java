// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecretMaskerTest {

    @Test
    void masksEverywhereInEveryLine() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (SecretMasker masker = new SecretMasker(sink, List.of("hunter2", ""))) {
            masker.write("token hunter2 and hunter2 again\nclean line\nlast hunter2".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(
                "token **** and **** again\nclean line\nlast ****", sink.toString(StandardCharsets.UTF_8));
    }

    @Test
    void maskHelperIgnoresEmptySecrets() {
        assertEquals("a **** b", SecretMasker.mask("a xyz b", List.of("xyz", "")));
    }
}
