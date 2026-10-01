package com.mobybank.harness.application;

import java.util.List;

public record TableDTO(List<String> cols, List<List<String>> rows) {
}
