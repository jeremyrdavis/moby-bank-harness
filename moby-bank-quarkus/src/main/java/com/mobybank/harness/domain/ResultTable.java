package com.mobybank.harness.domain;

import java.util.List;
import java.util.Objects;

/** A financial table returned by the agent. Every row has exactly one cell per column. */
public record ResultTable(List<String> cols, List<List<String>> rows) {

    public ResultTable {
        Objects.requireNonNull(cols, "columns required");
        Objects.requireNonNull(rows, "rows required");
        List<String> columns = List.copyOf(cols);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("a table needs at least one column");
        }
        rows = rows.stream()
                .map(row -> {
                    Objects.requireNonNull(row, "row required");
                    if (row.size() != columns.size()) {
                        throw new IllegalArgumentException(
                                "row has " + row.size() + " cells but the table has " + columns.size() + " columns");
                    }
                    return List.copyOf(row);
                })
                .toList();
        cols = columns;
    }
}
