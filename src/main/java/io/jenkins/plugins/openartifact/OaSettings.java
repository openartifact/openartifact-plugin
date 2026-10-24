// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;

/**
 * Plain value holders for the configuration of each operation. The pipeline step, build step and post-build action of
 * an operation all delegate to the same settings object so behaviour cannot drift between them.
 */
final class OaSettings {

    private OaSettings() {}

    /** Settings shared by every operation. */
    static class Common {
        String server;
        String installation;

        void setServer(@CheckForNull String server) {
            this.server = Util.fixEmptyAndTrim(server);
        }

        void setInstallation(@CheckForNull String installation) {
            this.installation = Util.fixEmptyAndTrim(installation);
        }
    }

    static final class Upload extends Common {
        final String repo;
        final String files;
        String targetPath;
        boolean flat;
        String properties;

        Upload(String repo, String files) {
            this.repo = Util.fixNull(repo).trim();
            this.files = Util.fixNull(files);
        }

        void setTargetPath(@CheckForNull String targetPath) {
            this.targetPath = Util.fixEmptyAndTrim(targetPath);
        }

        void setProperties(@CheckForNull String properties) {
            this.properties = Util.fixEmptyAndTrim(properties);
        }
    }

    static final class Bundle extends Common {
        final String name;
        final String version;
        String description;
        String pipeline;
        boolean fromBuild;
        String artifacts;
        boolean create = true;
        boolean seal = true;
        String gpgKey;

        Bundle(String name, String version) {
            this.name = Util.fixNull(name).trim();
            this.version = Util.fixNull(version).trim();
        }

        void setDescription(@CheckForNull String description) {
            this.description = Util.fixEmptyAndTrim(description);
        }

        void setPipeline(@CheckForNull String pipeline) {
            this.pipeline = Util.fixEmptyAndTrim(pipeline);
        }

        void setArtifacts(@CheckForNull String artifacts) {
            this.artifacts = Util.fixEmptyAndTrim(artifacts);
        }

        void setGpgKey(@CheckForNull String gpgKey) {
            this.gpgKey = Util.fixEmptyAndTrim(gpgKey);
        }
    }

    static final class Promote extends Common {
        final String bundle;
        final String version;
        final String stage;
        String targetRepo;
        String notes;

        Promote(String bundle, String version, String stage) {
            this.bundle = Util.fixNull(bundle).trim();
            this.version = Util.fixNull(version).trim();
            this.stage = Util.fixNull(stage).trim();
        }

        void setTargetRepo(@CheckForNull String targetRepo) {
            this.targetRepo = Util.fixEmptyAndTrim(targetRepo);
        }

        void setNotes(@CheckForNull String notes) {
            this.notes = Util.fixEmptyAndTrim(notes);
        }
    }
}
