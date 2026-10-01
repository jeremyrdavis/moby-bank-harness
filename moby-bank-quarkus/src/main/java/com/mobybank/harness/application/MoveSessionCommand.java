package com.mobybank.harness.application;

/** {@code target} is {@code local} or {@code cloud}. */
public record MoveSessionCommand(String sessionId, String target) {
}
