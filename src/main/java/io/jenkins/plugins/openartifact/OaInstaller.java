// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Downloads the {@code oa} CLI from GitHub releases on the node that will run it and verifies the archive against the
 * release's {@code SHA256SUMS} before unpacking.
 */
public class OaInstaller extends ToolInstaller {

    /** Version string that resolves to the newest published release. */
    public static final String LATEST = "latest";

    private String version = LATEST;
    private String downloadUrl;

    @DataBoundConstructor
    public OaInstaller(@CheckForNull String label) {
        super(label);
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    @DataBoundSetter
    public void setVersion(@CheckForNull String version) {
        String trimmed = Util.fixEmptyAndTrim(version);
        this.version = trimmed == null ? LATEST : trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
    }

    /**
     * Optional mirror: URL of the directory holding {@code oa_<version>_<os>_<arch>.tar.gz} (or {@code .zip}) and
     * {@code SHA256SUMS}. Defaults to the GitHub release of {@code version}.
     */
    @CheckForNull
    public String getDownloadUrl() {
        return downloadUrl;
    }

    @DataBoundSetter
    public void setDownloadUrl(@CheckForNull String downloadUrl) {
        this.downloadUrl = Util.fixEmptyAndTrim(downloadUrl);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {
        FilePath dir = preferredLocation(tool, node);
        dir.mkdirs();
        String installed = dir.act(new OaDownload(version, downloadUrl, log));
        log.getLogger().println(Messages.OaInstaller_Installed(installed, dir.getRemote()));
        return dir;
    }

    @Extension
    @Symbol("oa")
    public static class DescriptorImpl extends ToolInstallerDescriptor<OaInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OaInstaller_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == OaInstallation.class;
        }

        public FormValidation doCheckVersion(@QueryParameter String value) {
            String trimmed = Util.fixEmptyAndTrim(value);
            if (trimmed == null || LATEST.equals(trimmed) || trimmed.matches("v?\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?")) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.OaInstaller_VersionInvalid());
        }
    }
}
