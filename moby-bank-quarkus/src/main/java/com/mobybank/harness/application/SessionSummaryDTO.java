package com.mobybank.harness.application;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** One row of the conversation history. */
public record SessionSummaryDTO(
        String id,
        String title,
        @Schema(description = "Where the session's agent runs", enumeration = {"local", "cloud"}) String location,
        @Schema(description = "idle: ready; running: the agent is working; moving: being moved between locations",
                enumeration = {"idle", "running", "moving"}) String status,
        @Schema(description = "The recency label shown in the sidebar",
                enumeration = {"Today", "Previous 7 days", "Earlier"}) String group,
        Instant updatedAt) {
}
