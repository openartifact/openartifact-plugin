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

/** Pipeline step {@code oaUpload}; returns the parsed JSON result of the underlying {@code oa} command. */
public final class OaUploadStep extends Step {

    private final String repo;
    private final String files;
    private String server;
    private String installation;
    private String targetPath;
    private boolean flat;
    private String properties;

    @DataBoundConstructor
    public OaUploadStep(String repo, String files) {
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
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<Map<String, Object>> {

        private static final long serialVersionUID = 1L;

        private final transient OaUploadStep step;

        Execution(StepContext context, OaUploadStep step) {
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
            return OaOperations.upload(step.settings(), ctx);
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
            return "oaUpload";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaUploadStep_DisplayName();
        }

        public hudson.util.ListBoxModel doFillServerItems() {
            return OaForm.serverItems();
        }

        public hudson.util.ListBoxModel doFillInstallationItems() {
            return OaForm.installationItems();
        }
    }
}
