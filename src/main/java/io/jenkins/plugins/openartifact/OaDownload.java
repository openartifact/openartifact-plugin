// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.MasterToSlaveFileCallable;

/**
 * Runs on the node: resolves the release, downloads the {@code oa} archive for the node's platform, verifies its
 * SHA-256 against the release {@code SHA256SUMS}, and unpacks it into the tool directory.
 */
final class OaDownload extends MasterToSlaveFileCallable<String> {

    private static final long serialVersionUID = 1L;
    static final String RELEASES = "https://github.com/openartifact/openartifact/releases";
    static final String MARKER = ".oa-installed";
    private static final Pattern LATEST_TAG = Pattern.compile("/tag/v?([^/?#]+)$");
    private static final int TIMEOUT_MILLIS = 30_000;

    private final String version;
    private final String downloadUrl;
    private final TaskListener log;

    OaDownload(@NonNull String version, @CheckForNull String downloadUrl, @NonNull TaskListener log) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.log = log;
    }

    @Override
    public String invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
        PrintStream out = log.getLogger();
        String platform = platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        boolean windows = platform.startsWith("windows_");
        String resolved = version;
        if (OaInstaller.LATEST.equals(resolved)) {
            if (downloadUrl != null) {
                throw new IOException(Messages.OaInstaller_LatestWithMirror());
            }
            resolved = resolveLatest();
            out.println(Messages.OaInstaller_ResolvedLatest(resolved));
        }
        String base = downloadUrl != null
                ? stripTrailingSlash(downloadUrl)
                : RELEASES + "/download/v" + resolved;
        String asset = "oa_" + resolved + "_" + platform + (windows ? ".zip" : ".tar.gz");
        String expected = expectedSha256(base + "/SHA256SUMS", asset);

        Path root = dir.toPath();
        Path binary = root.resolve(OaInstallation.binaryName(!windows));
        Path marker = root.resolve(MARKER);
        String stamp = asset + ":" + expected;
        if (Files.isRegularFile(binary) && Files.isRegularFile(marker)
                && stamp.equals(Files.readString(marker, StandardCharsets.UTF_8).trim())) {
            return "oa " + resolved + " (already installed)";
        }

        Files.createDirectories(root);
        Path archive = Files.createTempFile(root, ".oa-download", ".tmp");
        try {
            out.println(Messages.OaInstaller_Downloading(base + "/" + asset));
            String actual = download(base + "/" + asset, archive);
            if (!actual.equalsIgnoreCase(expected)) {
                throw new IOException(Messages.OaInstaller_ChecksumMismatch(asset, expected, actual));
            }
            out.println(Messages.OaInstaller_ChecksumOk(asset));
            Files.deleteIfExists(binary);
            FilePath target = new FilePath(dir);
            try (InputStream in = Files.newInputStream(archive)) {
                if (windows) {
                    target.unzipFrom(in);
                } else {
                    target.untarFrom(in, FilePath.TarCompression.GZIP);
                }
            }
            if (!Files.isRegularFile(binary)) {
                throw new IOException(Messages.OaInstaller_BinaryMissingInArchive(asset, binary.getFileName()));
            }
            if (!binary.toFile().setExecutable(true, false) && !windows) {
                throw new IOException(Messages.OaInstaller_NotExecutable(binary.toString()));
            }
            Files.writeString(marker, stamp + "\n", StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(archive);
        }
        return "oa " + resolved;
    }

    /** Maps Java os.name/os.arch to the {@code <os>_<arch>} suffix used by release assets. */
    @NonNull
    static String platform(@NonNull String osName, @NonNull String osArch) throws IOException {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        String goOs;
        if (os.contains("linux")) {
            goOs = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            goOs = "darwin";
        } else if (os.contains("windows")) {
            goOs = "windows";
        } else {
            throw new IOException(Messages.OaInstaller_UnsupportedPlatform(osName, osArch));
        }
        String goArch;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            goArch = "amd64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            goArch = "arm64";
        } else {
            throw new IOException(Messages.OaInstaller_UnsupportedPlatform(osName, osArch));
        }
        return goOs + "_" + goArch;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String resolveLatest() throws IOException {
        URL url = URI.create(RELEASES + "/latest").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        try {
            String location = connection.getHeaderField("Location");
            if (location != null) {
                Matcher matcher = LATEST_TAG.matcher(location);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            throw new IOException(Messages.OaInstaller_LatestUnresolved(connection.getResponseCode()));
        } finally {
            connection.disconnect();
        }
    }

    /** Looks up {@code asset} in a {@code sha256sum}-format file. */
    @NonNull
    static String expectedSha256(@NonNull String sumsUrl, @NonNull String asset) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(open(sumsUrl).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length == 2) {
                    String name = parts[1].startsWith("*") ? parts[1].substring(1) : parts[1];
                    if (name.equals(asset) && parts[0].matches("[0-9a-fA-F]{64}")) {
                        return parts[0].toLowerCase(Locale.ROOT);
                    }
                }
            }
        }
        throw new IOException(Messages.OaInstaller_NotInChecksums(asset, sumsUrl));
    }

    /** Streams {@code url} to {@code target} and returns the hex SHA-256 of the bytes written. */
    @NonNull
    private static String download(@NonNull String url, @NonNull Path target) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(open(url).getInputStream(), digest)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static URLConnection open(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        return connection;
    }
}
