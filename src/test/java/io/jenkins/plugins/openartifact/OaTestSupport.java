// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.tools.ToolProperty;
import hudson.util.Secret;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

/** Shared fixtures: a fake {@code oa} on disk, a server with a token credential, and an installation pointing at it. */
final class OaTestSupport {

    static final String TOKEN = "s3cr3t-token-123";
    static final String CREDENTIALS_ID = "oa-token";
    static final String SERVER_URL = "https://oa.example.test";

    private OaTestSupport() {}

    /** Copies fake-oa.sh to {@code dir/oa}; returns {@code dir}. */
    static Path installFakeOa(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve("oa");
        try (InputStream in = OaTestSupport.class.getResourceAsStream("fake-oa.sh")) {
            if (in == null) {
                throw new IOException("fake-oa.sh missing from test resources");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
        return dir;
    }

    /** Registers the token, one server {@code main} and an installation {@code fake}; both are the defaults. */
    static void configure(Path oaDir) throws IOException {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, CREDENTIALS_ID, "oa token", Secret.fromString(TOKEN)));
        SystemCredentialsProvider.getInstance().save();
        OpenArtifactServer server = new OpenArtifactServer("main", SERVER_URL);
        server.setCredentialsId(CREDENTIALS_ID);
        OpenArtifactGlobalConfiguration config = OpenArtifactGlobalConfiguration.get();
        config.setServers(List.of(server));
        config.setDefaultServerId("main");
        Jenkins.get()
                .getDescriptorByType(OaInstallation.DescriptorImpl.class)
                .setInstallations(new OaInstallation("fake", oaDir.toString(), List.<ToolProperty<?>>of()));
        config.setDefaultInstallation("fake");
    }

    static List<String> calls(Path oaDir) throws IOException {
        Path log = oaDir.resolve("calls.log");
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    static String envLog(Path oaDir) throws IOException {
        Path log = oaDir.resolve("env.log");
        return Files.exists(log) ? Files.readString(log) : "";
    }
}
