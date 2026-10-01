package com.mobybank.harness.infrastructure.graph;

import com.mobybank.harness.domain.DocumentCatalog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds the OneDrive catalog from configuration ({@code harness.graph.*}). Nothing here contacts Microsoft until the
 * catalog is used, and it is only built when {@code harness.documents.mode=graph}.
 */
@ApplicationScoped
public class GraphAdapters {

    private final GraphSettings settings;
    private final Clock clock;

    @Inject
    public GraphAdapters(
            Clock clock,
            @ConfigProperty(name = "harness.graph.base-url", defaultValue = "https://graph.microsoft.com/v1.0") String baseUrl,
            @ConfigProperty(name = "harness.graph.drive", defaultValue = "me/drive") String drive,
            @ConfigProperty(name = "harness.graph.folders-root") Optional<String> foldersRoot,
            @ConfigProperty(name = "harness.graph.access-token") Optional<String> accessToken,
            @ConfigProperty(name = "harness.graph.token-url", defaultValue = "https://login.microsoftonline.com/common/oauth2/v2.0/token") String tokenUrl,
            @ConfigProperty(name = "harness.graph.tenant-id") Optional<String> tenantId,
            @ConfigProperty(name = "harness.graph.client-id") Optional<String> clientId,
            @ConfigProperty(name = "harness.graph.client-secret") Optional<String> clientSecret,
            @ConfigProperty(name = "harness.graph.request-timeout", defaultValue = "30s") Duration requestTimeout,
            @ConfigProperty(name = "harness.graph.max-download-bytes", defaultValue = "52428800") long maxDownloadBytes) {
        this.clock = clock;
        // A tenant id selects the tenant's own token endpoint unless a token URL was given explicitly.
        String resolvedTokenUrl = tenantId.filter(id -> !id.isBlank())
                .map(id -> "https://login.microsoftonline.com/" + id.strip() + "/oauth2/v2.0/token")
                .filter(url -> tokenUrl.equals("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
                .orElse(tokenUrl);
        this.settings = new GraphSettings(baseUrl, drive, foldersRoot, accessToken, resolvedTokenUrl, clientId,
                clientSecret, requestTimeout, maxDownloadBytes);
    }

    public DocumentCatalog catalog() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new GraphDocumentCatalog(settings, new GraphTokenProvider(settings, http, clock), http);
    }
}
