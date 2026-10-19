package com.mobybank.harness.application;

/** {@code location} is {@code local} or {@code cloud}: where a session that has not started yet will run. */
public record LaunchSessionCommand(String sessionId, String location) {
}
