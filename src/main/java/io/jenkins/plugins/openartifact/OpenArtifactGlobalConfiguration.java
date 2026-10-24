// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/** Global "OpenArtifact" section in <em>Manage Jenkins &rarr; System</em>. */
@Extension
@Symbol("openArtifact")
public class OpenArtifactGlobalConfiguration extends GlobalConfiguration {

    private List<OpenArtifactServer> servers = new ArrayList<>();
    private String defaultServerId;
    private String defaultInstallation;

    public OpenArtifactGlobalConfiguration() {
        load();
    }

    @NonNull
    public static OpenArtifactGlobalConfiguration get() {
        return GlobalConfiguration.all().getInstance(OpenArtifactGlobalConfiguration.class);
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Unclassified.class);
    }

    @NonNull
    public synchronized List<OpenArtifactServer> getServers() {
        return Collections.unmodifiableList(new ArrayList<>(servers));
    }

    @DataBoundSetter
    public synchronized void setServers(@CheckForNull List<OpenArtifactServer> servers) {
        this.servers = servers == null ? new ArrayList<>() : new ArrayList<>(servers);
        save();
    }

    @CheckForNull
    public synchronized String getDefaultServerId() {
        return defaultServerId;
    }

    @DataBoundSetter
    public synchronized void setDefaultServerId(@CheckForNull String defaultServerId) {
        this.defaultServerId = Util.fixEmptyAndTrim(defaultServerId);
        save();
    }

    /** Name of the {@link OaInstallation} used when a step does not name one; {@code null} means {@code oa} on PATH. */
    @CheckForNull
    public synchronized String getDefaultInstallation() {
        return defaultInstallation;
    }

    @DataBoundSetter
    public synchronized void setDefaultInstallation(@CheckForNull String defaultInstallation) {
        this.defaultInstallation = Util.fixEmptyAndTrim(defaultInstallation);
        save();
    }

    /**
     * Finds a server by id; a blank id selects the default server, or the only configured server.
     *
     * @return the server, or {@code null} when none matches
     */
    @CheckForNull
    public synchronized OpenArtifactServer findServer(@CheckForNull String id) {
        String wanted = Util.fixEmptyAndTrim(id);
        if (wanted == null) {
            wanted = defaultServerId;
        }
        if (wanted == null) {
            return servers.size() == 1 ? servers.get(0) : null;
        }
        for (OpenArtifactServer server : servers) {
            if (server.getId().equals(wanted)) {
                return server;
            }
        }
        return null;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // Drop the old list first so removed servers do not linger when the form omits the key.
        synchronized (this) {
            servers = new ArrayList<>();
            defaultServerId = null;
            defaultInstallation = null;
        }
        req.bindJSON(this, json);
        save();
        return true;
    }

    @NonNull
    public ListBoxModel doFillDefaultServerIdItems() {
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.OpenArtifactGlobalConfiguration_NoDefault(), "");
        for (OpenArtifactServer server : getServers()) {
            items.add(server.getId() + " (" + server.getUrl() + ")", server.getId());
        }
        return items;
    }

    @NonNull
    public ListBoxModel doFillDefaultInstallationItems() {
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.OpenArtifactGlobalConfiguration_PathOa(), "");
        for (OaInstallation installation : OaInstallation.installations()) {
            items.add(installation.getName());
        }
        return items;
    }
}
