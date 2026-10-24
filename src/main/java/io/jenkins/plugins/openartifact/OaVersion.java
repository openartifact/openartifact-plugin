// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The {@code major.minor.patch} core of an {@code oa} release version. */
record OaVersion(int major, int minor, int patch) implements Comparable<OaVersion> {

    private static final Pattern CORE = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)");

    /** @return the parsed version, or {@code null} for anything that is not a release number (e.g. {@code dev}) */
    @CheckForNull
    static OaVersion parse(@CheckForNull String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = CORE.matcher(text.trim());
        if (!matcher.lookingAt()) {
            return null;
        }
        try {
            return new OaVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(@NonNull OaVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
