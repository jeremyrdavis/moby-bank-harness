package com.mobybank.harness.domain;

import java.util.Objects;

public record AgentRepliedEvent(SessionId sessionId, MessageId messageId) implements DomainEvent {

    public AgentRepliedEvent {
        Objects.requireNonNull(sessionId, "session id required");
        Objects.requireNonNull(messageId, "message id required");
    }
}
