package com.mobybank.harness.infrastructure.graph;

import java.time.Duration;
import java.util.Optional;

/**
 * Settings for the OneDrive catalog.
 *
 * @param baseUrl          Graph root, such as https://graph.microsoft.com/v1.0
 * @param drive            which drive: me/drive (signed-in user), users/&lt;upn&gt;/drive or drives/&lt;id&gt;; app-only
 *                         tokens cannot use me/drive
 * @param foldersRoot      folder path whose sub-folders form the library; empty means the drive root
 * @param accessToken      a ready-made bearer token (for quick demos); otherwise client credentials are used
 * @param tokenUrl         the OAuth token endpoint for client credentials
 * @param clientId         the Entra app registration's client id
 * @param clientSecret     its client secret (supply it from the environment, never commit it)
 * @param requestTimeout   how long any one HTTP request may take
 * @param maxDownloadBytes the largest file the harness will download
 */
public record GraphSettings(String baseUrl, String drive, Optional<String> foldersRoot, Optional<String> accessToken,
                            String tokenUrl, Optional<String> clientId, Optional<String> clientSecret,
                            Duration requestTimeout, long maxDownloadBytes) {

    /** The drive's base URL, for example https://graph.microsoft.com/v1.0/me/drive. */
    String driveUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = drive.startsWith("/") ? drive.substring(1) : drive;
        return base + "/" + path;
    }
}
