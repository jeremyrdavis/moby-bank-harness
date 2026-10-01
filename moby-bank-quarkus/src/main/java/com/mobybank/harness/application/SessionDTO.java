package com.mobybank.harness.application;

import java.time.Instant;
import java.util.List;

/** A whole conversation. {@code moveTarget} is null unless the session is moving. */
public record SessionDTO(String id, String title, String location, String status, String moveTarget, String group,
                         Instant createdAt, Instant updatedAt, List<MessageDTO> messages, List<FileRefDTO> files) {
}
