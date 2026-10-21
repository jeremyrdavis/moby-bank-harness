package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.CurrentUserApplicationService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The signed-in analyst for the sidebar card of {@code index.html}: avatar, name, role and the sign-out button. */
@Path("/ui/user")
@Produces(MediaType.TEXT_HTML)
@Blocking
public class UserResource {

    private final CurrentUserApplicationService currentUser;
    private final boolean signOut;

    @Inject
    public UserResource(CurrentUserApplicationService currentUser,
                        @ConfigProperty(name = "harness.auth.mode", defaultValue = "login") String mode) {
        this.currentUser = currentUser;
        this.signOut = AuthMode.parse(mode) == AuthMode.LOGIN;
    }

    @GET
    public String user() {
        return Templates.user(currentUser.me(), signOut).render();
    }
}
