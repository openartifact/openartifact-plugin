// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;

/** The three operations (upload, bundle, promote) shared by pipeline steps, build steps and post-build actions. */
final class OaOperations {

    private OaOperations() {}

    @NonNull
    static OpenArtifactServer resolveServer(@CheckForNull String id) throws OaException {
        OpenArtifactGlobalConfiguration config = OpenArtifactGlobalConfiguration.get();
        OpenArtifactServer server = config.findServer(id);
        if (server != null) {
            return server;
        }
        if (id != null && !id.isBlank()) {
            throw new OaException(Messages.OaOperations_UnknownServer(id));
        }
        throw new OaException(Messages.OaOperations_NoServer());
    }

    @NonNull
    static Map<String, Object> upload(@NonNull OaSettings.Upload s, @NonNull OaContext ctx)
            throws IOException, InterruptedException {
        OpenArtifactServer server = resolveServer(s.server);
        String repo = ctx.env().expand(s.repo);
        List<String> files = splitList(ctx.env().expand(s.files));
        if (repo.isEmpty()) {
            throw new OaException(Messages.OaOperations_RepoRequired());
        }
        if (files.isEmpty()) {
            throw new OaException(Messages.OaOperations_FilesRequired());
        }

        List<String> args = new ArrayList<>();
        args.add("upload");
        args.add(repo);
        args.addAll(files);
        if (s.targetPath != null) {
            args.add("--target-path");
            args.add(ctx.env().expand(s.targetPath));
        }
        if (s.flat) {
            args.add("--flat");
        }
        for (String property : parseProperties(s.properties == null ? "" : ctx.env().expand(s.properties))) {
            args.add("--property");
            args.add(property);
        }
        Run<?, ?> run = ctx.run();
        args.add("--build-name");
        args.add(run.getParent().getFullName());
        args.add("--build-number");
        args.add(Integer.toString(run.getNumber()));
        String buildUrl = buildUrl(run);
        if (buildUrl != null) {
            args.add("--build-url");
            args.add(buildUrl);
        }
        String revision = ctx.env().get("GIT_COMMIT");
        if (revision != null && !revision.isBlank()) {
            args.add("--vcs-revision");
            args.add(revision.trim());
        }

        Map<String, Object> result;
        try {
            result = OaRunner.runJson(ctx, server, s.installation, args);
        } catch (OaException e) {
            // oa stops at the first failure; keep a badge for what it stored before that.
            record(ctx, server, repo, e.getOutput());
            throw e;
        }
        List<OaBuildAction.Artifact> artifacts = record(ctx, server, repo, result);
        if (artifacts.isEmpty()) {
            throw new OaException(Messages.OaOperations_NothingUploaded(repo));
        }
        ctx.listener().getLogger().println(Messages.OaOperations_Uploaded(artifacts.size(), repo));
        return result;
    }

    /** Adds the files listed under {@code uploaded} to the build's badge action. */
    @NonNull
    private static List<OaBuildAction.Artifact> record(
            @NonNull OaContext ctx,
            @NonNull OpenArtifactServer server,
            @NonNull String repo,
            @NonNull Map<String, Object> result) {
        Run<?, ?> run = ctx.run();
        List<OaBuildAction.Artifact> artifacts = new ArrayList<>();
        Map<String, List<OaBuildAction.Artifact>> byRepo = new LinkedHashMap<>();
        for (Map<String, Object> item : objects(result.get("uploaded"))) {
            String itemRepo = text(item.get("repo"), repo);
            OaBuildAction.Artifact artifact = new OaBuildAction.Artifact(
                    text(item.get("path"), ""),
                    text(item.get("sha256"), ""),
                    number(item.get("size")),
                    text(item.get("url"), ""));
            artifacts.add(artifact);
            byRepo.computeIfAbsent(itemRepo, k -> new ArrayList<>()).add(artifact);
        }
        if (artifacts.isEmpty()) {
            return artifacts;
        }
        OaBuildAction action = OaBuildAction.of(run);
        for (Map.Entry<String, List<OaBuildAction.Artifact>> entry : byRepo.entrySet()) {
            action.addUpload(server.getUrl(), entry.getKey(), entry.getValue());
        }
        try {
            run.save();
        } catch (IOException e) {
            ctx.listener().getLogger().println("Warning: cannot save build: " + e.getMessage());
        }
        return artifacts;
    }

    @NonNull
    static Map<String, Object> bundle(@NonNull OaSettings.Bundle s, @NonNull OaContext ctx)
            throws IOException, InterruptedException {
        OpenArtifactServer server = resolveServer(s.server);
        String name = ctx.env().expand(s.name);
        String version = ctx.env().expand(s.version);
        if (name.isEmpty() || version.isEmpty()) {
            throw new OaException(Messages.OaOperations_BundleRequired());
        }
        Run<?, ?> run = ctx.run();
        Map<String, Object> last = new LinkedHashMap<>();

        if (s.create) {
            List<String> args = new ArrayList<>(List.of("bundle", "create", name, version));
            if (s.description != null) {
                args.add("--description");
                args.add(ctx.env().expand(s.description));
            }
            if (s.pipeline != null) {
                args.add("--pipeline");
                args.add(ctx.env().expand(s.pipeline));
            }
            last = OaRunner.runJson(ctx, server, s.installation, args);
        }
        if (s.fromBuild) {
            last = OaRunner.runJson(
                    ctx,
                    server,
                    s.installation,
                    List.of("bundle", "add", name, version, "--from-build", fromBuild(run)));
        }
        if (s.artifacts != null) {
            Map<String, List<String>> byRepo = new LinkedHashMap<>();
            for (String entry : splitList(ctx.env().expand(s.artifacts))) {
                int colon = entry.indexOf(':');
                if (colon <= 0 || colon == entry.length() - 1) {
                    throw new OaException(Messages.OaOperations_ArtifactEntryInvalid(entry));
                }
                byRepo.computeIfAbsent(entry.substring(0, colon), k -> new ArrayList<>())
                        .add(entry.substring(colon + 1));
            }
            for (Map.Entry<String, List<String>> entry : byRepo.entrySet()) {
                List<String> args = new ArrayList<>(List.of("bundle", "add", name, version, "--repo", entry.getKey()));
                for (String path : entry.getValue()) {
                    args.add("--path");
                    args.add(path);
                }
                last = OaRunner.runJson(ctx, server, s.installation, args);
            }
        }
        if (s.seal) {
            List<String> args = new ArrayList<>(List.of("bundle", "seal", name, version));
            if (s.gpgKey != null) {
                args.add("--gpg-key");
                args.add(ctx.env().expand(s.gpgKey));
            }
            last = OaRunner.runJson(ctx, server, s.installation, args);
        }
        if (last.isEmpty()) {
            throw new OaException(Messages.OaOperations_BundleNothingToDo());
        }
        String state = text(last.get("state"), "");
        OaBuildAction.of(run).addBundle(new OaBuildAction.Bundle(server.getUrl(), name, version, state));
        run.save();
        ctx.listener().getLogger().println(Messages.OaOperations_Bundled(name, version, state));
        return last;
    }

    @NonNull
    static Map<String, Object> promote(@NonNull OaSettings.Promote s, @NonNull OaContext ctx)
            throws IOException, InterruptedException {
        OpenArtifactServer server = resolveServer(s.server);
        String bundle = ctx.env().expand(s.bundle);
        String version = ctx.env().expand(s.version);
        String stage = ctx.env().expand(s.stage);
        if (bundle.isEmpty() || version.isEmpty() || stage.isEmpty()) {
            throw new OaException(Messages.OaOperations_PromoteRequired());
        }
        List<String> args = new ArrayList<>(List.of("promote", bundle, version, "--stage", stage));
        if (s.targetRepo != null) {
            args.add("--target-repo");
            args.add(ctx.env().expand(s.targetRepo));
        }
        if (s.notes != null) {
            args.add("--notes");
            args.add(ctx.env().expand(s.notes));
        }
        Map<String, Object> result = OaRunner.runJson(ctx, server, s.installation, args);
        Map<String, Object> promoted = object(result.get("promoted"));
        int items = 0;
        Object rawItems = promoted.get("items");
        if (rawItems instanceof List<?> list) {
            items = list.size();
        } else if (rawItems instanceof Number n) {
            items = n.intValue();
        }
        Run<?, ?> run = ctx.run();
        OaBuildAction.of(run)
                .addPromotion(new OaBuildAction.Promotion(
                        server.getUrl(),
                        text(result.get("bundle"), bundle),
                        text(result.get("version"), version),
                        text(promoted.get("stage"), stage),
                        text(promoted.get("target_repo"), s.targetRepo),
                        items));
        run.save();
        ctx.listener().getLogger().println(Messages.OaOperations_Promoted(bundle, version, stage));
        return result;
    }

    /** {@code <job full name>/<build number>} as understood by {@code oa bundle add --from-build}. */
    @NonNull
    static String fromBuild(@NonNull Run<?, ?> run) {
        return run.getParent().getFullName() + "/" + run.getNumber();
    }

    @CheckForNull
    private static String buildUrl(@NonNull Run<?, ?> run) {
        String root = Jenkins.get().getRootUrl();
        return root == null ? null : root + run.getUrl();
    }

    /** Splits on newlines and commas, dropping blanks. */
    @NonNull
    static List<String> splitList(@CheckForNull String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[\\r\\n,]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Parses {@code key=value} lines; blank lines and {@code #} comments are skipped. */
    @NonNull
    static List<String> parseProperties(@CheckForNull String text) throws OaException {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new OaException(Messages.OaOperations_PropertyInvalid(trimmed));
            }
            out.add(trimmed);
        }
        return out;
    }

    @NonNull
    private static String text(@CheckForNull Object value, @CheckForNull String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        return value.toString();
    }

    private static long number(@CheckForNull Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(@CheckForNull Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    @NonNull
    private static List<Map<String, Object>> objects(@CheckForNull Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    out.add(object(item));
                }
            }
        }
        return out;
    }
}
