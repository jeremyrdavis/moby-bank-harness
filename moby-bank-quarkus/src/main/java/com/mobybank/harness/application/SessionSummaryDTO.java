package com.mobybank.harness.application;

import java.time.Instant;

/**
 * One row of the conversation history. {@code location} is {@code local} or {@code cloud}, {@code status} is
 * {@code idle}, {@code running} or {@code moving}, and {@code group} is the recency label shown in the sidebar.
 */
public record SessionSummaryDTO(String id, String title, String location, String status, String group,
                                Instant updatedAt) {
}
