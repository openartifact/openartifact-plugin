// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import java.util.Collections;
import java.util.Map;

/** A failure that is the user's to fix (bad configuration, rejected request, old CLI); reported without a stack trace. */
public class OaException extends AbortException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, Object> output;

    public OaException(String message) {
        this(message, null);
    }

    /**
     * @param output the JSON object oa printed before failing (e.g. the files an upload stored before it stopped), or
     *     {@code null}
     */
    OaException(String message, @CheckForNull Map<String, Object> output) {
        super(message);
        this.output = output == null ? Collections.emptyMap() : output;
    }

    /** What oa printed on stdout before failing; empty when it printed no JSON object. */
    @NonNull
    Map<String, Object> getOutput() {
        return output;
    }
}
