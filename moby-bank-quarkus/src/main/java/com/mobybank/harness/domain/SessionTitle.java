package com.mobybank.harness.domain;

import java.util.Objects;

/** A session's display title. Longer text is cut to {@link #MAX_LENGTH} characters. */
public record SessionTitle(String value) {

    public static final int MAX_LENGTH = 48;

    public static final SessionTitle NEW_CONVERSATION = new SessionTitle("New conversation");

    public SessionTitle {
        Objects.requireNonNull(value, "title required");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            value = value.substring(0, value.offsetByCodePoints(0, MAX_LENGTH)).stripTrailing();
        }
    }
}
