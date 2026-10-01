package com.mobybank.harness.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for moving a session; {@code target} must differ from where the session runs now. */
public record MoveSessionRequest(
        @Schema(description = "Where to move the session; must differ from where it runs now",
                enumeration = {"local", "cloud"}, required = true)
        String target) {
}
