package com.mobybank.harness.domain;

import java.util.Objects;

/** Raised by a {@link Session} when it finishes moving from one {@link Location} to the other. */
public record SessionMovedEvent(SessionId sessionId, Location from, Location to) implements DomainEvent {

    public SessionMovedEvent {
        Objects.requireNonNull(sessionId, "session id required");
        Objects.requireNonNull(from, "source location required");
        Objects.requireNonNull(to, "target location required");
    }
}
