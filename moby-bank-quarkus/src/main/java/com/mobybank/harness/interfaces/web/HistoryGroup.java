package com.mobybank.harness.interfaces.web;

import java.util.List;

/** One recency heading in the sidebar ("Today", …) and its conversations. */
record HistoryGroup(String label, List<Row> rows) {

    /** One conversation in the sidebar; {@code current} marks the one open in the main pane. */
    record Row(String id, String title, boolean cloud, boolean current) {
    }
}
