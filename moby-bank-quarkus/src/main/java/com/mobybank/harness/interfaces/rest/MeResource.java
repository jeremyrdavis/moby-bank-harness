package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.application.CurrentUserApplicationService;
import com.mobybank.harness.application.UserDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Endpoint for the signed-in analyst shown in the sidebar. */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User")
public class MeResource {

    private final CurrentUserApplicationService currentUser;

    @Inject
    public MeResource(CurrentUserApplicationService currentUser) {
        this.currentUser = currentUser;
    }

    @GET
    @Operation(summary = "The signed-in analyst", description = "Name, role and initials for the sidebar card.")
    public UserDTO me() {
        return currentUser.me();
    }
}
