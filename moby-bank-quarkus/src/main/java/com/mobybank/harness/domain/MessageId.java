package com.mobybank.harness.domain;

import java.util.Objects;
import java.util.UUID;

/** Identifies a {@link Message} within its {@link Session}. */
public record MessageId(UUID value) {

    public MessageId {
        Objects.requireNonNull(value, "message id required");
    }

    public static MessageId fresh() {
        return new MessageId(UUID.randomUUID());
    }
}
