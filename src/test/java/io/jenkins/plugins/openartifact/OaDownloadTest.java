// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import hudson.FilePath;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class OaDownloadTest {

    private HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private String base;

    @TempDir
    private Path dir;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = files.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/dl";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static byte[] tarGz(String name, String content) throws Exception {
        Path src = Files.createTempDirectory("oa-tar");
        try {
            Files.writeString(src.resolve(name), content);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(out)) {
                new FilePath(src.toFile()).tar(gz, "**");
            }
            return out.toByteArray();
        } finally {
            new FilePath(src.toFile()).deleteRecursive();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private String asset() throws IOException {
        return "oa_0.10.0_"
                + OaDownload.platform(System.getProperty("os.name"), System.getProperty("os.arch"))
                + ".tar.gz";
    }

    private OaDownload download() {
        return new OaDownload("0.10.0", base, StreamTaskListener.fromStderr());
    }

    @Test
    void downloadsVerifiesAndUnpacks() throws Exception {
        byte[] archive = tarGz("oa", "#!/bin/sh\necho hi\n");
        files.put("/dl/" + asset(), archive);
        files.put("/dl/SHA256SUMS", (sha256(archive) + "  " + asset() + "\n" + "0".repeat(64) + "  other.tar.gz\n")
                .getBytes(StandardCharsets.UTF_8));

        String result = download().invoke(dir.toFile(), null);

        assertEquals("oa 0.10.0", result);
        Path binary = dir.resolve("oa");
        assertTrue(Files.isExecutable(binary));
        assertEquals("#!/bin/sh\necho hi\n", Files.readString(binary));
        assertEquals("oa 0.10.0 (already installed)", download().invoke(dir.toFile(), null));
    }

    @Test
    void rejectsChecksumMismatchAndLeavesNothingBehind() throws Exception {
        files.put("/dl/" + asset(), tarGz("oa", "tampered"));
        files.put("/dl/SHA256SUMS", ("0".repeat(64) + "  " + asset() + "\n").getBytes(StandardCharsets.UTF_8));

        IOException e = assertThrows(IOException.class, () -> download().invoke(dir.toFile(), null));

        assertTrue(e.getMessage().contains("Checksum mismatch"), e.getMessage());
        assertFalse(Files.exists(dir.resolve("oa")));
        try (var listing = Files.list(dir)) {
            assertEquals(0, listing.count(), "temp archive must be removed");
        }
    }

    @Test
    void rejectsAssetMissingFromChecksums() throws Exception {
        files.put("/dl/" + asset(), tarGz("oa", "x"));
        files.put("/dl/SHA256SUMS", ("0".repeat(64) + "  something-else.tar.gz\n").getBytes(StandardCharsets.UTF_8));

        IOException e = assertThrows(IOException.class, () -> download().invoke(dir.toFile(), null));

        assertTrue(e.getMessage().contains("is not listed"), e.getMessage());
    }

    @Test
    void rejectsArchiveWithoutBinary() throws Exception {
        byte[] archive = tarGz("README", "no binary");
        files.put("/dl/" + asset(), archive);
        files.put("/dl/SHA256SUMS", (sha256(archive) + "  " + asset() + "\n").getBytes(StandardCharsets.UTF_8));

        IOException e = assertThrows(IOException.class, () -> download().invoke(dir.toFile(), null));

        assertTrue(e.getMessage().contains("does not contain"), e.getMessage());
    }

    @Test
    void mapsPlatforms() throws Exception {
        assertEquals("linux_amd64", OaDownload.platform("Linux", "amd64"));
        assertEquals("linux_arm64", OaDownload.platform("Linux", "aarch64"));
        assertEquals("darwin_arm64", OaDownload.platform("Mac OS X", "aarch64"));
        assertEquals("darwin_amd64", OaDownload.platform("Mac OS X", "x86_64"));
        assertEquals("windows_amd64", OaDownload.platform("Windows 11", "amd64"));
        assertThrows(IOException.class, () -> OaDownload.platform("FreeBSD", "amd64"));
        assertThrows(IOException.class, () -> OaDownload.platform("Linux", "riscv64"));
    }
}
