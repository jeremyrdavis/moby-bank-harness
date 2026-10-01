package com.mobybank.harness.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for starting a conversation. {@code location} is optional and defaults to local. */
public record CreateSessionRequest(
        @Schema(description = "Where the session starts", enumeration = {"local", "cloud"}, defaultValue = "local")
        String location) {
}
