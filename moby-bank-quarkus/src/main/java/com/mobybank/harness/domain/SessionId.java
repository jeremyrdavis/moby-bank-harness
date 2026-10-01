package com.mobybank.harness.domain;

import java.util.Objects;
import java.util.UUID;

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
