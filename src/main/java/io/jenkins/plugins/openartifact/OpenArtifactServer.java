// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.net.URI;
import java.net.URISyntaxException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/** A configured OpenArtifact server: stable id, base URL and an optional API token credential. */
public class OpenArtifactServer extends AbstractDescribableImpl<OpenArtifactServer> {

    private final String id;
    private final String url;
    private String credentialsId;

    @DataBoundConstructor
    public OpenArtifactServer(@NonNull String id, @NonNull String url) {
        this.id = Util.fixEmptyAndTrim(id) == null ? "" : id.trim();
        this.url = normalizeUrl(url);
    }

    @NonNull
    public String getId() {
        return id;
    }

    /** Base URL without trailing slash. */
    @NonNull
    public String getUrl() {
        return url;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    @NonNull
    static String normalizeUrl(String raw) {
        String trimmed = Util.fixNull(raw).trim();
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(0, end);
    }

    /**
     * Resolves the API token for this server in the context of {@code context}.
     *
     * @return the token or {@code null} when the server is used anonymously
     */
    @CheckForNull
    public Secret resolveToken(@CheckForNull Item context) {
        if (credentialsId == null) {
            return null;
        }
        StringCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItem(
                        StringCredentials.class,
                        context,
                        ACL.SYSTEM2,
                        URIRequirementBuilder.fromUri(url).build()),
                CredentialsMatchers.withId(credentialsId));
        return credentials == null ? null : credentials.getSecret();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<OpenArtifactServer> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OpenArtifactServer_DisplayName();
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item, @QueryParameter String url, @QueryParameter String credentialsId) {
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return new StandardListBoxModel().includeCurrentValue(credentialsId);
                }
            } else if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            item,
                            StringCredentials.class,
                            URIRequirementBuilder.fromUri(url).build(),
                            CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckId(@QueryParameter String id) {
            String trimmed = Util.fixEmptyAndTrim(id);
            if (trimmed == null) {
                return FormValidation.error(Messages.OpenArtifactServer_IdRequired());
            }
            if (!trimmed.matches("[A-Za-z0-9_.-]+")) {
                return FormValidation.error(Messages.OpenArtifactServer_IdInvalid());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(@QueryParameter String url) {
            String trimmed = Util.fixEmptyAndTrim(url);
            if (trimmed == null) {
                return FormValidation.error(Messages.OpenArtifactServer_UrlRequired());
            }
            try {
                URI uri = new URI(trimmed);
                String scheme = uri.getScheme();
                if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                    return FormValidation.error(Messages.OpenArtifactServer_UrlInvalid());
                }
            } catch (URISyntaxException e) {
                return FormValidation.error(Messages.OpenArtifactServer_UrlInvalid());
            }
            return FormValidation.ok();
        }

        /** Checks that the server answers on its health endpoint. */
        @POST
        public FormValidation doTestConnection(@QueryParameter String url) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            FormValidation valid = doCheckUrl(url);
            if (valid.kind != FormValidation.Kind.OK) {
                return valid;
            }
            try {
                int status = ServerProbe.healthStatus(normalizeUrl(url));
                if (status == 200) {
                    return FormValidation.ok(Messages.OpenArtifactServer_Reachable());
                }
                return FormValidation.error(Messages.OpenArtifactServer_Unhealthy(status));
            } catch (java.io.IOException e) {
                return FormValidation.error(e, Messages.OpenArtifactServer_Unreachable(e.getMessage()));
            }
        }
    }
}
