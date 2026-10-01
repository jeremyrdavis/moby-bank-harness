package com.mobybank.harness.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CreateSessionRequest(
        @Schema(description = "Where the session starts", enumeration = {"local", "cloud"}, defaultValue = "local")
        String location) {
}
