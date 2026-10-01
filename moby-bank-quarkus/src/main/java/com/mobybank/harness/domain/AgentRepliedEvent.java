package com.mobybank.harness.domain;

import java.util.Objects;

/** Raised by a {@link Session} when the agent's reply to the running turn is recorded; {@code messageId} is that reply. */
public record AgentRepliedEvent(SessionId sessionId, MessageId messageId) implements DomainEvent {

    public AgentRepliedEvent {
        Objects.requireNonNull(sessionId, "session id required");
        Objects.requireNonNull(messageId, "message id required");
    }
}
