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
import hudson.tasks.Recorder;
import hudson.tasks.Publisher;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** Freestyle post-build action running {@code oa}; see {@link OaOperations}. */
public final class OaUploadRecorder extends Recorder implements SimpleBuildStep {

    private final String repo;
    private final String files;
    private String server;
    private String installation;
    private String targetPath;
    private boolean flat;
    private String properties;

    @DataBoundConstructor
    public OaUploadRecorder(String repo, String files) {
        this.repo = repo;
        this.files = files;
    }

    public String getRepo() {
        return repo;
    }

    public String getFiles() {
        return files;
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
    public String getTargetPath() {
        return targetPath;
    }

    @DataBoundSetter
    public void setTargetPath(@CheckForNull String targetPath) {
        this.targetPath = Util.fixEmptyAndTrim(targetPath);
    }

    public boolean isFlat() {
        return flat;
    }

    @DataBoundSetter
    public void setFlat(boolean flat) {
        this.flat = flat;
    }

    @CheckForNull
    public String getProperties() {
        return properties;
    }

    @DataBoundSetter
    public void setProperties(@CheckForNull String properties) {
        this.properties = Util.fixEmptyAndTrim(properties);
    }

    OaSettings.Upload settings() {
        OaSettings.Upload s = new OaSettings.Upload(repo, files);
        s.setServer(server);
        s.setInstallation(installation);
        s.setTargetPath(targetPath);
        s.flat = flat;
        s.setProperties(properties);
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
        OaOperations.upload(settings(), new OaContext(run, workspace, launcher, listener, env));
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    @Symbol("oaUploadRecorder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaUploadRecorder_DisplayName();
        }

        public hudson.util.ListBoxModel doFillServerItems() {
            return OaForm.serverItems();
        }

        public hudson.util.ListBoxModel doFillInstallationItems() {
            return OaForm.installationItems();
        }
    }
}
