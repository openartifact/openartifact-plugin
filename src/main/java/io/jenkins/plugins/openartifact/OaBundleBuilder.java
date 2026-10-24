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
public final class OaBundleBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private final String version;
    private String server;
    private String installation;
    private String description;
    private String pipeline;
    private boolean fromBuild;
    private String artifacts;
    private boolean create = true;
    private boolean seal = true;
    private String gpgKey;

    @DataBoundConstructor
    public OaBundleBuilder(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
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
    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(@CheckForNull String description) {
        this.description = Util.fixEmptyAndTrim(description);
    }

    @CheckForNull
    public String getPipeline() {
        return pipeline;
    }

    @DataBoundSetter
    public void setPipeline(@CheckForNull String pipeline) {
        this.pipeline = Util.fixEmptyAndTrim(pipeline);
    }

    public boolean isFromBuild() {
        return fromBuild;
    }

    @DataBoundSetter
    public void setFromBuild(boolean fromBuild) {
        this.fromBuild = fromBuild;
    }

    @CheckForNull
    public String getArtifacts() {
        return artifacts;
    }

    @DataBoundSetter
    public void setArtifacts(@CheckForNull String artifacts) {
        this.artifacts = Util.fixEmptyAndTrim(artifacts);
    }

    public boolean isCreate() {
        return create;
    }

    @DataBoundSetter
    public void setCreate(boolean create) {
        this.create = create;
    }

    public boolean isSeal() {
        return seal;
    }

    @DataBoundSetter
    public void setSeal(boolean seal) {
        this.seal = seal;
    }

    @CheckForNull
    public String getGpgKey() {
        return gpgKey;
    }

    @DataBoundSetter
    public void setGpgKey(@CheckForNull String gpgKey) {
        this.gpgKey = Util.fixEmptyAndTrim(gpgKey);
    }

    OaSettings.Bundle settings() {
        OaSettings.Bundle s = new OaSettings.Bundle(name, version);
        s.setServer(server);
        s.setInstallation(installation);
        s.setDescription(description);
        s.setPipeline(pipeline);
        s.fromBuild = fromBuild;
        s.setArtifacts(artifacts);
        s.create = create;
        s.seal = seal;
        s.setGpgKey(gpgKey);
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
        OaOperations.bundle(settings(), new OaContext(run, workspace, launcher, listener, env));
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    @Symbol("oaBundleBuilder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaBundleBuilder_DisplayName();
        }

        public hudson.util.ListBoxModel doFillServerItems() {
            return OaForm.serverItems();
        }

        public hudson.util.ListBoxModel doFillInstallationItems() {
            return OaForm.installationItems();
        }
    }
}
