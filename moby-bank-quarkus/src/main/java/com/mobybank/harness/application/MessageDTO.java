package com.mobybank.harness.application;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A message as the API exposes it. */
public record MessageDTO(
        String id,
        @Schema(enumeration = {"user", "assistant"}) String role,
        @Schema(description = "The analyst's text; empty for assistant messages") String text,
        @Schema(description = "Attached files; empty for assistant messages") List<FileRefDTO> files,
        @Schema(description = "The agent's trace; empty for user messages") List<StepDTO> steps,
        @Schema(description = "The agent's prose, one entry per paragraph; empty for user messages")
        List<String> paragraphs,
        @Schema(description = "A financial table, or null when the agent returned none", nullable = true)
        TableDTO table,
        Instant createdAt) {
}
