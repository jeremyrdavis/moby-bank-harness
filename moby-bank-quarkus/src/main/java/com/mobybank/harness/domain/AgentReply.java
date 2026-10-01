package com.mobybank.harness.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What the agent produced for one turn: its trace, the prose answer, and optionally a financial table. */
public record AgentReply(List<Step> steps, List<String> paragraphs, Optional<ResultTable> table) {

    public AgentReply {
        Objects.requireNonNull(steps, "steps required");
        Objects.requireNonNull(paragraphs, "paragraphs required");
        Objects.requireNonNull(table, "table required (use Optional.empty())");
        steps = List.copyOf(steps);
        paragraphs = List.copyOf(paragraphs);
        if (paragraphs.isEmpty()) {
            throw new IllegalArgumentException("a reply needs at least one paragraph");
        }
    }
}
