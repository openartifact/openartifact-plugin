// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;

import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** Freestyle build step running {@code oa}; see {@link OaOperations}. */
public final class OaPromoteBuilder extends Builder implements SimpleBuildStep {

    private final String bundle;
    private final String version;
    private final String stage;
    private String server;
    private String installation;
    private String targetRepo;
    private String notes;

    @DataBoundConstructor
    public OaPromoteBuilder(String bundle, String version, String stage) {
        this.bundle = bundle;
        this.version = version;
        this.stage = stage;
    }

    public String getBundle() {
        return bundle;
    }

    public String getVersion() {
        return version;
    }

    public String getStage() {
        return stage;
    }

    @CheckForNull
    public String getServer() {
        return server;
    }

    @DataBoundSetter
    public void setServer(@CheckForNull String server) {
        this.server = Util.fixEmptyAndTrim(server);
    }

    @CheckForNull
    public String getInstallation() {
        return installation;
    }

    @DataBoundSetter
    public void setInstallation(@CheckForNull String installation) {
        this.installation = Util.fixEmptyAndTrim(installation);
    }

    @CheckForNull
    public String getTargetRepo() {
        return targetRepo;
    }

    @DataBoundSetter
    public void setTargetRepo(@CheckForNull String targetRepo) {
        this.targetRepo = Util.fixEmptyAndTrim(targetRepo);
    }

    @CheckForNull
    public String getNotes() {
        return notes;
    }

    @DataBoundSetter
    public void setNotes(@CheckForNull String notes) {
        this.notes = Util.fixEmptyAndTrim(notes);
    }

    OaSettings.Promote settings() {
        OaSettings.Promote s = new OaSettings.Promote(bundle, version, stage);
        s.setServer(server);
        s.setInstallation(installation);
        s.setTargetRepo(targetRepo);
        s.setNotes(notes);
        return s;
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        OaOperations.promote(settings(), new OaContext(run, workspace, launcher, listener, env));
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    @Symbol("oaPromoteBuilder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaPromoteBuilder_DisplayName();
        }

        public hudson.util.ListBoxModel doFillServerItems() {
            return OaForm.serverItems();
        }

        public hudson.util.ListBoxModel doFillInstallationItems() {
            return OaForm.installationItems();
        }
    }
}
