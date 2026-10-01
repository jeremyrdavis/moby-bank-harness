package com.mobybank.harness.application;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A file attached to a message. */
public record FileRefDTO(
        @Schema(description = "File name, without any path") String name,
        @Schema(description = "Where the file came from", enumeration = {"upload", "onedrive"}) String source) {
}
