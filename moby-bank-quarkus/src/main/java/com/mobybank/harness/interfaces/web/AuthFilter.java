package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.AuthenticationApplicationService;
import com.mobybank.harness.application.RequestIdentity;
import com.mobybank.harness.application.UserDTO;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Finds out who is calling. Everything under {@code /api} and {@code /ui} needs a signed-in user when
 * {@code harness.auth.mode=login}: a request without a valid session cookie gets 401 (the page's requests also get an
 * {@code HX-Redirect} to the sign-in page). Requests that change something must come from the page itself ({@code
 * HX-Request}) or carry JSON, which a form on another site cannot send; this is on top of the cookie's
 * {@code SameSite=Lax}. {@code /auth} and the static files are open.
 */
public class AuthFilter {

    /** The cookie that holds the session token. */
    public static final String COOKIE = "moby-login";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AuthenticationApplicationService auth;
    private final RequestIdentity identity;
    private final AuthMode mode;

    @Inject
    public AuthFilter(AuthenticationApplicationService auth, RequestIdentity identity,
                      @ConfigProperty(name = "harness.auth.mode", defaultValue = "login") String mode) {
        this.auth = auth;
        this.identity = identity;
        this.mode = AuthMode.parse(mode);
    }

    // Runs on the thread of the endpoint it guards: a worker thread for every endpoint here, so the session lookup may block.
    @ServerRequestFilter
    public Optional<Response> filter(ContainerRequestContext request) {
        String path = "/" + request.getUriInfo().getPath().replaceFirst("^/+", "");
        boolean api = path.startsWith("/api/") || path.equals("/api");
        boolean ui = path.startsWith("/ui/") || path.equals("/ui");
        if (!api && !ui) {
            return Optional.empty();
        }
        if (mode == AuthMode.NONE) {
            return Optional.empty(); // everybody is the demo user (see RequestCurrentUser)
        }
        Cookie cookie = request.getCookies().get(COOKIE);
        Optional<UserDTO> user = auth.resolve(cookie == null ? null : cookie.getValue());
        if (user.isEmpty()) {
            Log.debugf("Refusing %s %s: not signed in", request.getMethod(), path);
            return Optional.of(unauthorized(ui));
        }
        if (!SAFE_METHODS.contains(request.getMethod()) && !fromThePageOrJson(request)) {
            Log.debugf("Refusing %s %s: not from the page", request.getMethod(), path);
            return Optional.of(Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"forbidden\",\"message\":\"This request did not come from the page.\"}").build());
        }
        identity.signedInAs(user.get().id());
        return Optional.empty();
    }

    private static boolean fromThePageOrJson(ContainerRequestContext request) {
        if ("true".equalsIgnoreCase(request.getHeaderString("HX-Request"))) {
            return true;
        }
        String type = request.getHeaderString("Content-Type");
        return type != null && type.toLowerCase().startsWith(MediaType.APPLICATION_JSON);
    }

    private static Response unauthorized(boolean ui) {
        Response.ResponseBuilder response = Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"unauthorized\",\"message\":\"Sign in to continue.\"}");
        if (ui) {
            response.header("HX-Redirect", "/login.html");
        }
        return response.build();
    }
}
