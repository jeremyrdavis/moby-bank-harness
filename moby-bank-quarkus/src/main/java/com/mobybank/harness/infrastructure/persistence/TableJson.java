package com.mobybank.harness.infrastructure.persistence;

import java.util.List;

/** A result table as stored in a message's JSON column. */
public record TableJson(List<String> cols, List<List<String>> rows) {
}
