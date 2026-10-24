// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.LineTransformationOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Replaces every occurrence of the given secrets with {@code ****} in the stream passing through. */
final class SecretMasker extends LineTransformationOutputStream {

    static final String MASK = "****";

    private final OutputStream delegate;
    private final List<String> secrets = new ArrayList<>();
    private final Charset charset;

    SecretMasker(@NonNull OutputStream delegate, @NonNull Collection<String> secrets, @NonNull Charset charset) {
        this.delegate = delegate;
        this.charset = charset;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                this.secrets.add(secret);
            }
        }
    }

    SecretMasker(@NonNull OutputStream delegate, @NonNull Collection<String> secrets) {
        this(delegate, secrets, StandardCharsets.UTF_8);
    }

    @NonNull
    static String mask(@NonNull String text, @NonNull Collection<String> secrets) {
        String result = text;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                result = result.replace(secret, MASK);
            }
        }
        return result;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        delegate.write(mask(new String(b, 0, len, charset), secrets).getBytes(charset));
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        super.close();
        delegate.flush();
    }
}
