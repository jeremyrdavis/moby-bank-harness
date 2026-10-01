package com.mobybank.harness.domain;

import java.util.Objects;

/** Identifies the analyst. Authentication is out of scope, so the demo runs as a single fixed user. */
public record UserId(String value) {

    public static final UserId DEMO = new UserId("demo-analyst");

    public UserId {
        Objects.requireNonNull(value, "user id required");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("user id must not be blank");
        }
    }
}
