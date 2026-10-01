package com.mobybank.harness.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record MoveSessionRequest(
        @Schema(description = "Where to move the session; must differ from where it runs now",
                enumeration = {"local", "cloud"}, required = true)
        String target) {
}
