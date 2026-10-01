package com.mobybank.harness.infrastructure.sbx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the line-by-line JSON the Claude Code CLI prints with {@code --output-format stream-json --verbose}. Only
 * what the harness shows is kept: the tools the agent used (as steps), the agent's conversation id, and the final
 * answer. Lines that are not JSON, and events of kinds this parser does not know, are ignored, so a newer agent
 * version adds noise instead of breaking turns.
 */
final class ClaudeStreamParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> READ_TOOLS = Set.of("Read", "NotebookRead", "Glob", "Grep", "LS");
    private static final Set<String> IGNORED_TOOLS = Set.of("TodoWrite");
    private static final int LABEL_LIMIT = 140;

    private final Consumer<Step> onStep;
    private final List<Step> steps = new ArrayList<>();
    private String sessionId;
    private String resultText;
    private boolean error;
    private String lastAssistantText = "";

    ClaudeStreamParser(Consumer<Step> onStep) {
        this.onStep = onStep;
    }

    void accept(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return;
        }
        JsonNode event;
        try {
            event = JSON.readTree(trimmed);
        } catch (Exception e) {
            return;
        }
        if (event.hasNonNull("session_id")) {
            sessionId = event.get("session_id").asText();
        }
        switch (event.path("type").asText("")) {
            case "assistant" -> assistant(event.path("message").path("content"));
            case "result" -> {
                if (event.hasNonNull("result")) {
                    resultText = event.get("result").asText();
                }
                error = event.path("is_error").asBoolean(false);
            }
            default -> { }
        }
    }

    /** The steps seen so far, in order. */
    List<Step> steps() {
        return List.copyOf(steps);
    }

    Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    /** The agent's final answer, when it sent a result event. */
    Optional<String> resultText() {
        return Optional.ofNullable(resultText);
    }

    /** The text of the agent's most recent message, for when no result event arrived. */
    String lastAssistantText() {
        return lastAssistantText;
    }

    /** True when the result event said the agent ended in error. */
    boolean isError() {
        return error;
    }

    private void assistant(JsonNode content) {
        if (!content.isArray()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            switch (block.path("type").asText("")) {
                case "text" -> text.append(block.path("text").asText(""));
                case "tool_use" -> toStep(block.path("name").asText(""), block.path("input")).ifPresent(step -> {
                    steps.add(step);
                    onStep.accept(step);
                });
                default -> { }
            }
        }
        if (!text.isEmpty()) {
            lastAssistantText = text.toString();
        }
    }

    static Optional<Step> toStep(String tool, JsonNode input) {
        if (tool.isBlank() || IGNORED_TOOLS.contains(tool)) {
            return Optional.empty();
        }
        StepKind kind = READ_TOOLS.contains(tool) ? StepKind.READ : StepKind.COMPUTE;
        String detail = switch (tool) {
            case "Read" -> first(input, "file_path", "path");
            case "NotebookRead" -> first(input, "notebook_path", "path");
            case "Glob", "Grep" -> prefixed(tool.toLowerCase(), first(input, "pattern"));
            case "LS" -> prefixed("ls", first(input, "path"));
            case "Bash" -> first(input, "command");
            default -> prefixed(tool, firstTextValue(input));
        };
        String label = detail.isBlank() ? tool : detail.strip().lines().findFirst().orElse(tool);
        if (label.length() > LABEL_LIMIT) {
            label = label.substring(0, LABEL_LIMIT - 1) + "…";
        }
        return Optional.of(new Step(kind, label));
    }

    private static String first(JsonNode input, String... fields) {
        for (String field : fields) {
            if (input.hasNonNull(field) && !input.get(field).asText().isBlank()) {
                return input.get(field).asText();
            }
        }
        return "";
    }

    private static String firstTextValue(JsonNode input) {
        for (JsonNode value : input) {
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static String prefixed(String prefix, String detail) {
        return detail.isBlank() ? prefix : prefix + " " + detail;
    }
}
