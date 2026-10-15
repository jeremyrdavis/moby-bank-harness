package com.mobybank.harness.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the agent produced for one turn: its trace, the prose answer, optionally a financial table, and optionally a
 * notice about the environment the answer came from (for example that the sandbox had to be started again). A notice is
 * not part of the conversation: it is shown to the analyst but never replayed to the agent.
 */
public record AgentReply(List<Step> steps, List<String> paragraphs, Optional<ResultTable> table,
                         Optional<String> notice) {

    public AgentReply {
        Objects.requireNonNull(steps, "steps required");
        Objects.requireNonNull(paragraphs, "paragraphs required");
        Objects.requireNonNull(table, "table required (use Optional.empty())");
        Objects.requireNonNull(notice, "notice required (use Optional.empty())");
        steps = List.copyOf(steps);
        paragraphs = List.copyOf(paragraphs);
        notice = notice.map(String::strip).filter(text -> !text.isEmpty());
        if (paragraphs.isEmpty()) {
            throw new IllegalArgumentException("a reply needs at least one paragraph");
        }
    }

    /** A reply without a notice. */
    public AgentReply(List<Step> steps, List<String> paragraphs, Optional<ResultTable> table) {
        this(steps, paragraphs, table, Optional.empty());
    }

    /** The same reply with a notice for the analyst. */
    public AgentReply withNotice(String text) {
        return new AgentReply(steps, paragraphs, table, Optional.of(text));
    }
}
