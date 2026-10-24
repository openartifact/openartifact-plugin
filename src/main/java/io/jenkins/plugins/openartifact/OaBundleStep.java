// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.util.Map;
import java.util.Set;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** Pipeline step {@code oaBundle}; returns the parsed JSON result of the underlying {@code oa} command. */
public final class OaBundleStep extends Step {

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
    public OaBundleStep(String name, String version) {
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
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Map<String, Object>> {

        private static final long serialVersionUID = 1L;

        private final transient OaBundleStep step;

        Execution(StepContext context, OaBundleStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Map<String, Object> run() throws Exception {
            StepContext context = getContext();
            OaContext ctx = new OaContext(
                    context.get(Run.class),
                    context.get(FilePath.class),
                    context.get(Launcher.class),
                    context.get(TaskListener.class),
                    context.get(EnvVars.class));
            return OaOperations.bundle(step.settings(), ctx);
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, FilePath.class, Launcher.class, TaskListener.class, EnvVars.class);
        }

        @Override
        public String getFunctionName() {
            return "oaBundle";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaBundleStep_DisplayName();
        }

        public hudson.util.ListBoxModel doFillServerItems() {
            return OaForm.serverItems();
        }

        public hudson.util.ListBoxModel doFillInstallationItems() {
            return OaForm.installationItems();
        }
    }
}
