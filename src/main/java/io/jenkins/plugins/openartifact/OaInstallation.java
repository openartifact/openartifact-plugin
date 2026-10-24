// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.model.EnvironmentSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A named {@code oa} CLI installation: either a preinstalled binary (directory or file path, per node via
 * tool-location overrides) or one downloaded by {@link OaInstaller}.
 */
public class OaInstallation extends ToolInstallation
        implements NodeSpecific<OaInstallation>, EnvironmentSpecific<OaInstallation> {

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public OaInstallation(
            @NonNull String name, @CheckForNull String home, @CheckForNull List<? extends ToolProperty<?>> properties) {
        super(name, home == null ? "" : home, properties);
    }

    @NonNull
    public static OaInstallation[] installations() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class).getInstallations();
    }

    @CheckForNull
    public static OaInstallation find(@CheckForNull String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (OaInstallation installation : installations()) {
            if (installation.getName().equals(name)) {
                return installation;
            }
        }
        return null;
    }

    @Override
    public OaInstallation forEnvironment(EnvVars environment) {
        return new OaInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public OaInstallation forNode(@NonNull Node node, TaskListener log) throws IOException, InterruptedException {
        return new OaInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    /**
     * Resolves the {@code oa} executable on the machine behind {@code launcher}.
     *
     * @return the absolute executable path on that machine
     * @throws IOException if no executable exists at the configured home
     */
    @NonNull
    public String getExecutable(@NonNull Launcher launcher) throws IOException, InterruptedException {
        String home = getHome();
        if (home == null || home.isBlank()) {
            throw new IOException(Messages.OaInstallation_NoHome(getName()));
        }
        FilePath home0 = new FilePath(launcher.getChannel(), home);
        FilePath binary = home0.isDirectory() ? home0.child(binaryName(launcher.isUnix())) : home0;
        if (!binary.exists()) {
            throw new IOException(Messages.OaInstallation_MissingBinary(getName(), binary.getRemote()));
        }
        return binary.getRemote();
    }

    @NonNull
    static String binaryName(boolean unix) {
        return unix ? "oa" : "oa.exe";
    }

    @Extension
    @Symbol("oa")
    public static class DescriptorImpl extends ToolDescriptor<OaInstallation> {

        public DescriptorImpl() {
            load();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaInstallation_DisplayName();
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new OaInstaller(null));
        }

        @Override
        public void setInstallations(OaInstallation... installations) {
            super.setInstallations(installations);
            save();
        }

        @NonNull
        @Override
        public OaInstallation[] getInstallations() {
            return super.getInstallations();
        }

        /** The home may be blank when an installer provisions the binary. */
        @Override
        public FormValidation doCheckHome(@QueryParameter File value) {
            return FormValidation.ok();
        }
    }
}
