// SPDX-FileCopyrightText: The OpenArtifact Authors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package io.jenkins.plugins.openartifact;

import hudson.ProxyConfiguration;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/** Minimal controller-side reachability probe used by the "Test connection" button. */
final class ServerProbe {

    private static final int TIMEOUT_MILLIS = 10_000;

    private ServerProbe() {}

    static int healthStatus(String baseUrl) throws IOException {
        URL url = URI.create(baseUrl + "/health").toURL();
        HttpURLConnection connection = (HttpURLConnection) ProxyConfiguration.open(url);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }
}
