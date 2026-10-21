package com.mobybank.harness.interfaces.web;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Stops the application at startup, with a message that names the setting, when "Sign in with Microsoft" is switched on
 * but has no authority URL to sign in at. Without this the failure shows up later, as a redirect to the wrong server.
 */
@ApplicationScoped
public class MicrosoftSignInCheck {

    private final boolean enabled;
    private final Optional<String> authServerUrl;
    private final Optional<String> clientId;

    public MicrosoftSignInCheck(
            @ConfigProperty(name = "harness.auth.microsoft.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "quarkus.oidc.auth-server-url") Optional<String> authServerUrl,
            @ConfigProperty(name = "quarkus.oidc.client-id") Optional<String> clientId) {
        this.enabled = enabled;
        this.authServerUrl = authServerUrl.filter(url -> !url.isBlank());
        this.clientId = clientId.filter(id -> !id.isBlank());
    }

    void onStart(@Observes StartupEvent event) {
        check();
    }

    void check() {
        if (!enabled) {
            return;
        }
        if (authServerUrl.isEmpty()) {
            throw new IllegalStateException("harness.auth.microsoft.enabled=true needs QUARKUS_OIDC_AUTH_SERVER_URL "
                    + "(for example https://login.microsoftonline.com/<tenant id>/v2.0, or .../consumers/v2.0 for personal "
                    + "accounts); see docs/authentication.md");
        }
        if (clientId.isEmpty()) {
            throw new IllegalStateException("harness.auth.microsoft.enabled=true needs QUARKUS_OIDC_CLIENT_ID, the "
                    + "application (client) id of the Entra app registration; see docs/authentication.md");
        }
    }
}
