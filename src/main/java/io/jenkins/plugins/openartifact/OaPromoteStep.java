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

/** Pipeline step {@code oaPromote}; returns the parsed JSON result of the underlying {@code oa} command. */
public final class OaPromoteStep extends Step {

    private final String bundle;
    private final String version;
    private final String stage;
    private String server;
    private String installation;
    private String targetRepo;
    private String notes;

    @DataBoundConstructor
    public OaPromoteStep(String bundle, String version, String stage) {
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
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Map<String, Object>> {

        private static final long serialVersionUID = 1L;

        private final transient OaPromoteStep step;

        Execution(StepContext context, OaPromoteStep step) {
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
            return OaOperations.promote(step.settings(), ctx);
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
            return "oaPromote";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaPromoteStep_DisplayName();
        }

        public hudson.util.ListBoxModel doFillServerItems() {
            return OaForm.serverItems();
        }

        public hudson.util.ListBoxModel doFillInstallationItems() {
            return OaForm.installationItems();
        }
    }
}
