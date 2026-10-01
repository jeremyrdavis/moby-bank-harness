package com.mobybank.harness.application;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A whole conversation. */
public record SessionDTO(
        String id,
        String title,
        @Schema(description = "Where the session's agent runs", enumeration = {"local", "cloud"}) String location,
        @Schema(description = "idle: ready; running: the agent is working; moving: being moved between locations",
                enumeration = {"idle", "running", "moving"}) String status,
        @Schema(description = "Where the session is moving to; null unless status is moving",
                enumeration = {"local", "cloud"}, nullable = true) String moveTarget,
        @Schema(description = "The recency label shown in the sidebar",
                enumeration = {"Today", "Previous 7 days", "Earlier"}) String group,
        Instant createdAt,
        Instant updatedAt,
        List<MessageDTO> messages,
        @Schema(description = "Distinct files shared anywhere in the conversation, in the order they first appeared")
        List<FileRefDTO> files) {
}
