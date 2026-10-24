// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;

/**
 * Runs the {@code oa} CLI on the build's node and parses its {@code --format json} output. The API token travels only
 * in the {@code OA_TOKEN} environment variable of the child process and is masked in everything echoed to the log.
 */
final class OaRunner {

    /** First oa release shipping the {@code upload}, {@code bundle} and {@code promote} commands. */
    static final OaVersion MIN_VERSION = new OaVersion(0, 10, 0);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT = new TypeReference<>() {};

    private OaRunner() {}

    /**
     * Runs {@code oa --format json <args...>} and returns the parsed JSON object printed on stdout.
     *
     * @throws OaException when oa cannot be found, is too old, exits non-zero or prints something that is not a JSON
     *     object
     */
    @NonNull
    static Map<String, Object> runJson(
            @NonNull OaContext ctx,
            @NonNull OpenArtifactServer server,
            @CheckForNull String installationName,
            @NonNull List<String> args)
            throws IOException, InterruptedException {
        // A pipeline node may not have touched its workspace yet; the child process needs its working directory.
        ctx.workspace().mkdirs();
        String executable = resolveExecutable(ctx, installationName);
        EnvVars env = new EnvVars(ctx.env());
        env.put("OA_SERVER", server.getUrl());
        env.remove("OA_TOKEN");
        List<String> secrets = new ArrayList<>();
        Secret token = server.resolveToken(ctx.run().getParent());
        if (token != null) {
            String plain = token.getPlainText();
            env.put("OA_TOKEN", plain);
            secrets.add(plain);
        } else if (server.getCredentialsId() != null) {
            throw new OaException(Messages.OaRunner_CredentialsNotFound(server.getCredentialsId(), server.getId()));
        }

        checkVersion(ctx, executable, env, secrets);

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--format");
        command.add("json");
        command.addAll(args);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrCopy = new ByteArrayOutputStream();
        PrintStream log = ctx.listener().getLogger();
        try (SecretMasker maskedStdout = new SecretMasker(stdout, secrets);
                SecretMasker maskedStderr = new SecretMasker(tee(log, stderrCopy), secrets)) {
            int exit = ctx.launcher()
                    .launch()
                    .cmds(command)
                    .envs(env)
                    .pwd(ctx.workspace())
                    .stdout(maskedStdout)
                    .stderr(maskedStderr)
                    .join();
            maskedStdout.flush();
            maskedStderr.flush();
            if (exit != 0) {
                Map<String, Object> partial = tryParseObject(stdout.toString(StandardCharsets.UTF_8));
                Object reported = partial.get("error");
                String detail = reported != null ? reported.toString() : tail(stderrCopy);
                throw new OaException(
                        Messages.OaRunner_Failed(args.isEmpty() ? "" : args.get(0), exit, detail), partial);
            }
        }
        return parseObject(stdout.toString(StandardCharsets.UTF_8), args);
    }

    /** The JSON object in {@code text}, or an empty map when there is none. */
    @NonNull
    private static Map<String, Object> tryParseObject(@NonNull String text) {
        if (text.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = JSON.readValue(text, OBJECT);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    @NonNull
    static Map<String, Object> parseObject(@NonNull String text, @NonNull List<String> args) throws OaException {
        try {
            Map<String, Object> parsed = JSON.readValue(text, OBJECT);
            if (parsed == null) {
                throw new OaException(Messages.OaRunner_BadJson(args.isEmpty() ? "" : args.get(0), "null"));
            }
            return parsed;
        } catch (IOException e) {
            throw new OaException(Messages.OaRunner_BadJson(args.isEmpty() ? "" : args.get(0), e.getMessage()));
        }
    }

    @NonNull
    private static String resolveExecutable(@NonNull OaContext ctx, @CheckForNull String installationName)
            throws IOException, InterruptedException {
        String name = installationName;
        if (name == null || name.isBlank()) {
            name = OpenArtifactGlobalConfiguration.get().getDefaultInstallation();
        }
        if (name == null || name.isBlank()) {
            return ctx.launcher().isUnix() ? "oa" : "oa.exe";
        }
        OaInstallation installation = OaInstallation.find(name);
        if (installation == null) {
            throw new OaException(Messages.OaRunner_UnknownInstallation(name));
        }
        Computer computer = ctx.workspace().toComputer();
        Node node = computer == null ? null : computer.getNode();
        if (node == null) {
            node = Jenkins.get();
        }
        installation = installation.forNode(node, ctx.listener()).forEnvironment(ctx.env());
        return installation.getExecutable(ctx.launcher());
    }

    private static void checkVersion(
            @NonNull OaContext ctx, @NonNull String executable, @NonNull EnvVars env, @NonNull List<String> secrets)
            throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = ctx.launcher()
                .launch()
                .cmds(executable, "--format", "json", "version")
                .envs(env)
                .pwd(ctx.workspace())
                .quiet(true)
                .stdout(out)
                .stderr(err)
                .join();
        if (exit != 0) {
            throw new OaException(Messages.OaRunner_VersionFailed(
                    executable, exit, SecretMasker.mask(tail(err), secrets)));
        }
        String text = out.toString(StandardCharsets.UTF_8);
        String reported = reportedVersion(text);
        OaVersion version = OaVersion.parse(reported);
        if (version == null) {
            ctx.listener().getLogger().println(Messages.OaRunner_UnknownVersion(reported == null ? text.trim() : reported));
            return;
        }
        if (version.compareTo(MIN_VERSION) < 0) {
            throw new OaException(Messages.OaRunner_TooOld(version.toString(), MIN_VERSION.toString()));
        }
    }

    /** Reads the version from {@code {"version":"x.y.z"}}, falling back to the text form {@code oa version x.y.z}. */
    @CheckForNull
    static String reportedVersion(@NonNull String text) {
        try {
            Map<String, Object> parsed = JSON.readValue(text, OBJECT);
            Object value = parsed == null ? null : parsed.get("version");
            if (value != null) {
                return value.toString();
            }
        } catch (IOException e) {
            // Not JSON: older plain-text output.
        }
        String trimmed = text.trim();
        String prefix = "oa version ";
        return trimmed.startsWith(prefix) ? trimmed.substring(prefix.length()).trim() : null;
    }

    private static PrintStream tee(@NonNull PrintStream first, @NonNull ByteArrayOutputStream second) {
        return new PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                first.write(b);
                second.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                first.write(b, off, len);
                second.write(b, off, len);
            }

            @Override
            public void flush() {
                first.flush();
            }
        }, false, StandardCharsets.UTF_8);
    }

    /** Last few lines of captured stderr, for error messages. */
    @NonNull
    private static String tail(@NonNull ByteArrayOutputStream stream) {
        String[] lines = stream.toString(StandardCharsets.UTF_8).strip().split("\\R");
        int from = Math.max(0, lines.length - 5);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length)).strip();
    }
}
