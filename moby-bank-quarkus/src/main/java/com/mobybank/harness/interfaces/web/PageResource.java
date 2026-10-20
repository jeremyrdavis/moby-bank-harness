package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.AuthenticationApplicationService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Serves {@code index.html} at {@code /} and {@code /index.html}, but only to a signed-in user (or to anyone with
 * {@code harness.auth.mode=none}); everybody else is sent to the sign-in page. The page is a classpath resource, not a
 * public static file, so it cannot be fetched around this check.
 */
@Path("/")
public class PageResource {

    static final String PAGE = "pages/index.html";

    private final AuthenticationApplicationService auth;
    private final AuthMode mode;

    @Inject
    public PageResource(AuthenticationApplicationService auth,
                        @ConfigProperty(name = "harness.auth.mode", defaultValue = "login") String mode) {
        this.auth = auth;
        this.mode = AuthMode.parse(mode);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response root(@CookieParam(AuthFilter.COOKIE) String token) throws IOException {
        return page(token);
    }

    @GET
    @Path("/index.html")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public Response indexHtml(@CookieParam(AuthFilter.COOKIE) String token) throws IOException {
        return page(token);
    }

    private Response page(String token) throws IOException {
        if (mode == AuthMode.LOGIN && auth.resolve(token).isEmpty()) {
            return Response.seeOther(URI.create("/login.html")).build();
        }
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(PAGE)) {
            if (in == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(in.readAllBytes()).type(MediaType.TEXT_HTML + "; charset=utf-8")
                    .header("Cache-Control", "no-store").build();
        }
    }
}
