// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import jenkins.model.RunAction2;

/**
 * Per-build record of what this build did in OpenArtifact. It renders small badges in the build history row and on
 * the build page, plus a summary panel on the build page that lists every uploaded artifact.
 *
 * <p>Only plain Jenkins APIs are used; the badge-plugin is not required.
 */
public final class OaBuildAction implements RunAction2, BuildBadgeAction {

    private final List<Upload> uploads = new ArrayList<>();
    private final List<Bundle> bundles = new ArrayList<>();
    private final List<Promotion> promotions = new ArrayList<>();

    private transient Run<?, ?> run;

    /** Returns the build's action, creating and attaching it on first use. */
    @NonNull
    public static OaBuildAction of(@NonNull Run<?, ?> run) {
        OaBuildAction existing = run.getAction(OaBuildAction.class);
        if (existing != null) {
            return existing;
        }
        synchronized (run) {
            existing = run.getAction(OaBuildAction.class);
            if (existing != null) {
                return existing;
            }
            OaBuildAction created = new OaBuildAction();
            run.addAction(created);
            return created;
        }
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @CheckForNull
    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/openartifact/images/openartifact-mark-color.svg";
    }

    @Override
    public String getDisplayName() {
        return Messages.OaBuildAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "openartifact";
    }

    /** Jelly-facing: the plugin resource of the OpenArtifact mark. */
    @NonNull
    public String getMarkUrl() {
        return "/plugin/openartifact/images/openartifact-mark-color.svg";
    }

    @NonNull
    public synchronized List<Upload> getUploads() {
        return Collections.unmodifiableList(new ArrayList<>(uploads));
    }

    @NonNull
    public synchronized List<Bundle> getBundles() {
        return Collections.unmodifiableList(new ArrayList<>(bundles));
    }

    @NonNull
    public synchronized List<Promotion> getPromotions() {
        return Collections.unmodifiableList(new ArrayList<>(promotions));
    }

    public synchronized int getUploadedCount() {
        int total = 0;
        for (Upload upload : uploads) {
            total += upload.artifacts.size();
        }
        return total;
    }

    /** Whether there is anything to show. */
    public synchronized boolean isRecorded() {
        return !uploads.isEmpty() || !bundles.isEmpty() || !promotions.isEmpty();
    }

    synchronized void addUpload(@NonNull String serverUrl, @NonNull String repo, @NonNull List<Artifact> artifacts) {
        for (Upload existing : uploads) {
            if (existing.serverUrl.equals(serverUrl) && existing.repo.equals(repo)) {
                existing.artifacts.addAll(artifacts);
                return;
            }
        }
        uploads.add(new Upload(serverUrl, repo, artifacts));
    }

    synchronized void addBundle(@NonNull Bundle bundle) {
        bundles.removeIf(b -> b.serverUrl.equals(bundle.serverUrl)
                && b.name.equals(bundle.name)
                && b.version.equals(bundle.version));
        bundles.add(bundle);
    }

    synchronized void addPromotion(@NonNull Promotion promotion) {
        promotions.add(promotion);
    }

    /** Only {@code http} and {@code https} links are ever rendered. */
    @CheckForNull
    public static String safeLink(@CheckForNull String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        try {
            String scheme = URI.create(link.trim()).getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return link.trim();
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** An uploaded file. */
    public static final class Artifact implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String path;
        private final String sha256;
        private final long size;
        private final String url;

        Artifact(@NonNull String path, @CheckForNull String sha256, long size, @CheckForNull String url) {
            this.path = path;
            this.sha256 = sha256 == null ? "" : sha256;
            this.size = size;
            this.url = url == null ? "" : url;
        }

        public String getPath() {
            return path;
        }

        public String getSha256() {
            return sha256;
        }

        public String getShortSha256() {
            return sha256.length() > 12 ? sha256.substring(0, 12) : sha256;
        }

        public long getSize() {
            return size;
        }

        public String getSizeText() {
            return humanSize(size);
        }

        @CheckForNull
        public String getLink() {
            return safeLink(url);
        }
    }

    /** All uploads of one build into one repository of one server. */
    public static final class Upload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String serverUrl;
        private final String repo;
        private final List<Artifact> artifacts;

        Upload(@NonNull String serverUrl, @NonNull String repo, @NonNull List<Artifact> artifacts) {
            this.serverUrl = serverUrl;
            this.repo = repo;
            this.artifacts = new ArrayList<>(artifacts);
        }

        public String getRepo() {
            return repo;
        }

        public List<Artifact> getArtifacts() {
            return Collections.unmodifiableList(artifacts);
        }

        public int getCount() {
            return artifacts.size();
        }

        /** The repository page in the OpenArtifact UI. */
        @CheckForNull
        public String getLink() {
            return safeLink(serverUrl + "/repos/" + enc(repo));
        }
    }

    /** A bundle this build created or extended. */
    public static final class Bundle implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String serverUrl;
        private final String name;
        private final String version;
        private final String state;

        Bundle(
                @NonNull String serverUrl,
                @NonNull String name,
                @NonNull String version,
                @CheckForNull String state) {
            this.serverUrl = serverUrl;
            this.name = name;
            this.version = version;
            this.state = state == null ? "" : state;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getState() {
            return state;
        }

        @CheckForNull
        public String getLink() {
            return bundleLink(serverUrl, name, version);
        }
    }

    /** A promotion of a bundle to a stage. */
    public static final class Promotion implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String serverUrl;
        private final String bundle;
        private final String version;
        private final String stage;
        private final String targetRepo;
        private final int items;

        Promotion(
                @NonNull String serverUrl,
                @NonNull String bundle,
                @NonNull String version,
                @NonNull String stage,
                @CheckForNull String targetRepo,
                int items) {
            this.serverUrl = serverUrl;
            this.bundle = bundle;
            this.version = version;
            this.stage = stage;
            this.targetRepo = targetRepo == null ? "" : targetRepo;
            this.items = items;
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

        public String getTargetRepo() {
            return targetRepo;
        }

        public int getItems() {
            return items;
        }

        @CheckForNull
        public String getLink() {
            return bundleLink(serverUrl, bundle, version);
        }
    }

    @CheckForNull
    private static String bundleLink(String serverUrl, String name, String version) {
        return safeLink(serverUrl + "/bundles/" + enc(name) + "/" + enc(version));
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
