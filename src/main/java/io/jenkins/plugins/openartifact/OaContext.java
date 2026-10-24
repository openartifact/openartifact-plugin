// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/** Everything an operation needs from the surrounding build. */
record OaContext(
        @NonNull Run<?, ?> run,
        @NonNull FilePath workspace,
        @NonNull Launcher launcher,
        @NonNull TaskListener listener,
        @NonNull EnvVars env) {}
