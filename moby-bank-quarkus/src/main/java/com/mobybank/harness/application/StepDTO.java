package com.mobybank.harness.application;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** One entry of an agent trace. */
public record StepDTO(
        @Schema(description = "A file read, or a calculation run", enumeration = {"read", "compute"}) String kind,
        @Schema(description = "The file path, or the calculation, as the agent reported it") String label) {
}
