package com.mobybank.harness.domain;

import java.util.Objects;
import java.util.UUID;

/** Identifies a {@link Session}. Wrapping the UUID keeps it from being mixed up with a {@link MessageId}. */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "session id required");
    }

    public static SessionId fresh() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId parse(String text) {
        return new SessionId(UUID.fromString(text));
    }
}
