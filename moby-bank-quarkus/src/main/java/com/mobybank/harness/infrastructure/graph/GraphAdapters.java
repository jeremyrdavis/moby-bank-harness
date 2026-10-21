package com.mobybank.harness.infrastructure.graph;

import com.mobybank.harness.application.UserGraphTokens;
import com.mobybank.harness.domain.DocumentCatalog;
import io.quarkus.logging.Log;
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
    private final UserGraphTokens userTokens;
    private final boolean microsoftEnabled;
    private final String userTokenUrl;
    private final Optional<String> oidcClientId;
    private final Optional<String> oidcSecret;

    @Inject
    public GraphAdapters(
            Clock clock,
            UserGraphTokens userTokens,
            @ConfigProperty(name = "harness.auth.microsoft.enabled", defaultValue = "false") boolean microsoftEnabled,
            @ConfigProperty(name = "harness.graph.user-token-url") Optional<String> userTokenUrl,
            @ConfigProperty(name = "quarkus.oidc.auth-server-url") Optional<String> oidcServerUrl,
            @ConfigProperty(name = "quarkus.oidc.client-id") Optional<String> oidcClientId,
            @ConfigProperty(name = "quarkus.oidc.credentials.secret") Optional<String> oidcSecret,
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
        this.userTokens = userTokens;
        this.microsoftEnabled = microsoftEnabled;
        this.oidcClientId = oidcClientId.filter(id -> !id.isBlank());
        this.oidcSecret = oidcSecret.filter(secret -> !secret.isBlank());
        // A tenant id selects the tenant's own token endpoint unless a token URL was given explicitly.
        String resolvedTokenUrl = tenantId.filter(id -> !id.isBlank())
                .map(id -> "https://login.microsoftonline.com/" + id.strip() + "/oauth2/v2.0/token")
                .filter(url -> tokenUrl.equals("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
                .orElse(tokenUrl);
        this.settings = new GraphSettings(baseUrl, drive, foldersRoot, accessToken, resolvedTokenUrl, clientId,
                clientSecret, requestTimeout, maxDownloadBytes);
        // Users' tokens are renewed at the token endpoint of the tenant they signed in to.
        this.userTokenUrl = userTokenUrl.filter(url -> !url.isBlank())
                .or(() -> oidcServerUrl.filter(url -> !url.isBlank()).map(GraphAdapters::tokenUrlOf))
                .orElse(resolvedTokenUrl);
    }

    /** {@code https://login.microsoftonline.com/<tenant>/v2.0} has its token endpoint at {@code .../oauth2/v2.0/token}. */
    static String tokenUrlOf(String authServerUrl) {
        String base = authServerUrl.strip().replaceAll("/+$", "").replaceAll("/v2\\.0$", "");
        return base + "/oauth2/v2.0/token";
    }

    public DocumentCatalog catalog() {
        Log.infof("Using OneDrive through Microsoft Graph (%s, drive %s)", settings.baseUrl(), settings.drive());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        boolean appWideCredential = settings.accessToken().filter(token -> !token.isBlank()).isPresent()
                || (settings.clientId().isPresent() && settings.clientSecret().isPresent());
        if (!microsoftEnabled) {
            // the app-wide credential is the only one; its constructor says what is missing
            return new GraphDocumentCatalog(settings,
                    GraphTokenSource.appWide(new GraphTokenProvider(settings, http, clock)), http);
        }
        GraphTokenProvider appWide = appWideCredential ? new GraphTokenProvider(settings, http, clock) : null;
        MicrosoftTokenRefresher refresher = new MicrosoftTokenRefresher(userTokenUrl,
                oidcClientId.or(settings::clientId).orElse(""), oidcSecret.or(settings::clientSecret).orElse(null), http,
                settings.requestTimeout(), clock);
        Log.infof("OneDrive is read as each signed-in user (their own Microsoft token)%s",
                appWide == null ? "" : ", with the app-wide credential for users who signed in another way");
        return new GraphDocumentCatalog(settings, new UserGraphTokenSource(userTokens, appWide, refresher, clock), http);
    }
}
