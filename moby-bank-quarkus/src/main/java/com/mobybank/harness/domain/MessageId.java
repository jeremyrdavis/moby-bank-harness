package com.mobybank.harness.domain;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {

    public MessageId {
        Objects.requireNonNull(value, "message id required");
    }

    public static MessageId fresh() {
        return new MessageId(UUID.randomUUID());
    }
}
