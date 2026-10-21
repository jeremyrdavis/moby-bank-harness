package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.AuthOptionsDTO;
import com.mobybank.harness.application.AuthenticationApplicationService;
import com.mobybank.harness.application.InvalidCredentialsException;
import com.mobybank.harness.application.LoginResult;
import com.mobybank.harness.application.MicrosoftSignIn;
import com.mobybank.harness.application.MicrosoftTokens;
import io.quarkus.logging.Log;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.IdToken;
import io.quarkus.oidc.RefreshToken;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;

/**
 * Signing in and out: the email and password form, the Microsoft sign-in, and what the sign-in page may offer. A
 * successful sign-in of either kind ends in the same session cookie ({@link AuthFilter#COOKIE}), then redirects to the
 * page. Failures redirect back to the sign-in page with an {@code error} code it knows how to word.
 */
@Path("/auth")
public class AuthResource {

    private final AuthenticationApplicationService auth;
    private final boolean microsoftEnabled;
    private final Duration sessionTtl;

    // The OpenID Connect beans can only be injected as fields, and lazily: with Microsoft sign-in switched off they
    // do not exist.
    @Inject
    Instance<SecurityIdentity> identity;

    @Inject
    @IdToken
    Instance<JsonWebToken> idToken;

    @Inject
    Instance<AccessTokenCredential> accessToken;

    @Inject
    Instance<RefreshToken> refreshToken;

    @Inject
    public AuthResource(AuthenticationApplicationService auth,
                        @ConfigProperty(name = "harness.auth.microsoft.enabled", defaultValue = "false") boolean microsoftEnabled,
                        @ConfigProperty(name = "harness.auth.session-ttl", defaultValue = "8h") Duration sessionTtl) {
        this.auth = auth;
        this.microsoftEnabled = microsoftEnabled;
        this.sessionTtl = sessionTtl;
    }

    /** What the sign-in page offers: the password form always, the Microsoft button when it is set up. */
    @GET
    @Path("/options")
    @Produces(MediaType.APPLICATION_JSON)
    public AuthOptionsDTO options() {
        return auth.options();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Blocking
    public Response login(@RestForm("email") String email, @RestForm("password") String password,
                          @Context UriInfo uri) {
        try {
            return signedIn(auth.loginWithPassword(email, password), uri);
        } catch (InvalidCredentialsException e) {
            return redirect("/login.html?error=" + (e.lockedOut() ? "locked" : "credentials"));
        }
    }

    /**
     * The end of the Microsoft sign-in. The OpenID Connect extension protects this path: an anonymous visitor is sent
     * to Microsoft, and comes back here signed in, with the ID token and the Graph tokens. We turn that into our own
     * session and remember the Graph tokens for the user's OneDrive.
     */
    @GET
    @Path("/microsoft")
    @Blocking
    public Response microsoft(@Context UriInfo uri) {
        if (!microsoftEnabled) {
            return redirect("/login.html?error=unavailable");
        }
        try {
            JsonWebToken id = idToken.get();
            String tenant = claim(id, "tid");
            String subject = claim(id, "oid") != null ? claim(id, "oid") : id.getSubject();
            if (subject == null) {
                throw new IllegalStateException("the ID token has no subject");
            }
            String email = firstNonBlank(claim(id, "email"), claim(id, "preferred_username"));
            MicrosoftTokens tokens = new MicrosoftTokens(accessToken.get().getToken(),
                    refreshToken.isResolvable() ? refreshToken.get().getToken() : null, expiry());
            LoginResult result = auth.signInWithMicrosoft(new MicrosoftSignIn(
                    tenant == null ? subject : tenant + ":" + subject, email, claim(id, "name"), tokens));
            return signedIn(result, uri);
        } catch (RuntimeException e) {
            Log.warnf(e, "Microsoft sign-in could not be completed");
            return redirect("/login.html?error=microsoft");
        }
    }

    /**
     * Where the OpenID Connect extension sends the browser when Microsoft refused the sign-in or the flow failed (the
     * user said no, a scope was rejected, the state did not match). The reason goes to the log, not to the page.
     */
    @GET
    @Path("/error")
    public Response error(@QueryParam("error") String error, @QueryParam("error_description") String description) {
        Log.warnf("Microsoft sign-in failed: %s %s", error, description);
        return redirect("/login.html?error=microsoft");
    }

    @POST
    @Path("/logout")
    @Blocking
    public Response logout(@CookieParam(AuthFilter.COOKIE) String token, @Context UriInfo uri) {
        auth.logout(token);
        NewCookie expired = cookie("", Duration.ZERO, uri);
        return Response.seeOther(URI.create("/login.html")).cookie(expired).build();
    }

    private Response signedIn(LoginResult result, UriInfo uri) {
        return Response.seeOther(URI.create("/")).cookie(cookie(result.token(), sessionTtl, uri)).build();
    }

    private static NewCookie cookie(String value, Duration maxAge, UriInfo uri) {
        return new NewCookie.Builder(AuthFilter.COOKIE).value(value).path("/").httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX).secure("https".equalsIgnoreCase(uri.getRequestUri().getScheme()))
                .maxAge((int) maxAge.toSeconds()).build();
    }

    private static Response redirect(String location) {
        return Response.seeOther(URI.create(location)).build();
    }

    /** When the access token stops working: the identity's expiry if it has one, else an hour from now. */
    private Instant expiry() {
        Object expiresIn = identity.get().getAttribute("access_token_expires_at");
        if (expiresIn instanceof Number seconds) {
            return Instant.ofEpochSecond(seconds.longValue());
        }
        return Instant.now().plus(Duration.ofHours(1));
    }

    private static String claim(JsonWebToken token, String name) {
        Object value = token.getClaim(name);
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
