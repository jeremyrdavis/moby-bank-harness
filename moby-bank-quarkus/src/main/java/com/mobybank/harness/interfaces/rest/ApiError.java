package com.mobybank.harness.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The body of every error response. {@code error} is a stable code; {@code message} is for people. */
@Schema(description = "Error response")
public record ApiError(
        @Schema(description = "Stable error code: bad_request, not_found, conflict, sandbox_failure or document_source_failure") String error,
        @Schema(description = "Human-readable explanation") String message) {
}
