package com.mobybank.harness.application;

import java.time.Instant;
import java.util.List;

/**
 * A message as the API exposes it. {@code role} is {@code user} or {@code assistant}; {@code table} is null when the
 * agent returned none.
 */
public record MessageDTO(String id, String role, String text, List<FileRefDTO> files, List<StepDTO> steps,
                         List<String> paragraphs, TableDTO table, Instant createdAt) {
}
