package com.mobybank.harness.application;

/** {@code location} is {@code local} or {@code cloud}; null or blank means local. */
public record CreateSessionCommand(String location) {
}
