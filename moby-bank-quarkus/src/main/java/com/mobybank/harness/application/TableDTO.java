package com.mobybank.harness.application;

import java.util.List;

/** A financial table the agent returned: column headings and rows of cells, one cell per column. */
public record TableDTO(List<String> cols, List<List<String>> rows) {
}
