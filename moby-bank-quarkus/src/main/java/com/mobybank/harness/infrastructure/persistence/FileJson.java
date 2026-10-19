package com.mobybank.harness.infrastructure.persistence;

/** A file reference as stored in a message's JSON column. */
public record FileJson(String name, String source) {
}
