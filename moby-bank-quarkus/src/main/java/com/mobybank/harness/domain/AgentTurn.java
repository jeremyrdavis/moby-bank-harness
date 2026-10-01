package com.mobybank.harness.domain;

import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of one turn handed to the sandbox agent: where the session runs, the conversation so far
 * and the new user message. A snapshot, not the aggregate, because the agent works outside any aggregate update.
 */
public record AgentTurn(SessionId sessionId, Location location, List<Message> history, Message userMessage) {

    public AgentTurn {
        Objects.requireNonNull(sessionId, "session id required");
        Objects.requireNonNull(location, "location required");
        Objects.requireNonNull(history, "history required");
        Objects.requireNonNull(userMessage, "user message required");
        history = List.copyOf(history);
        if (userMessage.role() != MessageRole.USER) {
            throw new IllegalArgumentException("a turn starts from a user message");
        }
    }
}
