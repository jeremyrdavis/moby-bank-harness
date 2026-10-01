package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns the agent's Markdown answer into what the UI shows: paragraphs, plus the first Markdown table (if any)
 * as a structured table. Other tables stay inside the paragraph text.
 */
final class ReplyFormatter {

    private static final Pattern ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");
    static final String EMPTY_ANSWER = "The agent finished without a written answer.";

    private ReplyFormatter() {
    }

    static AgentReply toReply(String text, List<Step> steps) {
        List<String> lines = new ArrayList<>(text.strip().lines().toList());
        Optional<ResultTable> table = Optional.empty();

        for (int i = 0; i + 1 < lines.size(); i++) {
            if (ROW.matcher(lines.get(i)).matches() && SEPARATOR.matcher(lines.get(i + 1)).matches()) {
                List<String> header = cells(lines.get(i));
                List<List<String>> rows = new ArrayList<>();
                int end = i + 2;
                while (end < lines.size() && ROW.matcher(lines.get(end)).matches()) {
                    rows.add(fit(cells(lines.get(end)), header.size()));
                    end++;
                }
                table = Optional.of(new ResultTable(header, rows));
                lines.subList(i, end).clear();
                break;
            }
        }

        List<String> paragraphs = List.of(String.join("\n", lines).split("\\n\\s*\\n")).stream()
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .toList();
        return new AgentReply(steps, paragraphs.isEmpty() ? List.of(EMPTY_ANSWER) : paragraphs, table);
    }

    private static List<String> cells(String row) {
        String inner = row.strip();
        if (inner.startsWith("|")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        return java.util.Arrays.stream(inner.split("\\|", -1)).map(String::strip).toList();
    }

    /** Pads or cuts a row so it has one cell per column. */
    private static List<String> fit(List<String> row, int width) {
        List<String> fitted = new ArrayList<>(row);
        while (fitted.size() < width) {
            fitted.add("");
        }
        return fitted.subList(0, width);
    }
}
