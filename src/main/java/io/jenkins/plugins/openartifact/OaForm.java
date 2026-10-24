// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.ListBoxModel;

/** Drop-down models shared by every step and build-step form. */
final class OaForm {

    private OaForm() {}

    @NonNull
    static ListBoxModel serverItems() {
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.OaForm_DefaultServer(), "");
        for (OpenArtifactServer server : OpenArtifactGlobalConfiguration.get().getServers()) {
            items.add(server.getId() + " (" + server.getUrl() + ")", server.getId());
        }
        return items;
    }

    @NonNull
    static ListBoxModel installationItems() {
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.OaForm_DefaultInstallation(), "");
        for (OaInstallation installation : OaInstallation.installations()) {
            items.add(installation.getName());
        }
        return items;
    }
}
