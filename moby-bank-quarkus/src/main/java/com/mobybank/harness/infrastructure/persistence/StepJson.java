package com.mobybank.harness.infrastructure.persistence;

/** One agent step as stored in a message's JSON column. */
public record StepJson(String kind, String label) {
}
