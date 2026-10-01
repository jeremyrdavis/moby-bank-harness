package com.mobybank.harness.infrastructure.sbx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClaudeStreamParserTest {

    private final List<Step> streamed = new ArrayList<>();
    private final ClaudeStreamParser parser = new ClaudeStreamParser(streamed::add);

    private static String assistant(String... blocks) {
        return "{\"type\":\"assistant\",\"message\":{\"role\":\"assistant\",\"content\":[" + String.join(",", blocks)
                + "]},\"session_id\":\"s-1\"}";
    }

    private static String tool(String name, String inputJson) {
        return "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"" + name + "\",\"input\":" + inputJson + "}";
    }

    private static String text(String text) {
        return "{\"type\":\"text\",\"text\":\"" + text + "\"}";
    }

    @Test
    void aFullRunYieldsStepsSessionIdAndTheFinalAnswer() {
        parser.accept("{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"abc-123\",\"tools\":[\"Read\"]}");
        parser.accept(assistant(tool("Read", "{\"file_path\":\"/home/agent/workspace/files/10-Q.pdf\"}")));
        parser.accept("{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"content\":\"...\"}]}}");
        parser.accept(assistant(tool("Bash", "{\"command\":\"python3 leverage.py\"}")));
        parser.accept(assistant(text("Leverage rose.")));
        parser.accept("{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"Leverage rose to 2.9x.\",\"session_id\":\"abc-123\"}");

        assertEquals(List.of(new Step(StepKind.READ, "/home/agent/workspace/files/10-Q.pdf"),
                new Step(StepKind.COMPUTE, "python3 leverage.py")), parser.steps());
        assertEquals(parser.steps(), streamed, "steps are emitted as they are read");
        assertEquals(Optional.of("abc-123"), parser.sessionId());
        assertEquals(Optional.of("Leverage rose to 2.9x."), parser.resultText());
        assertEquals("Leverage rose.", parser.lastAssistantText());
        assertFalse(parser.isError());
    }

    @Test
    void readToolsBecomeReadsAndEverythingElseACompute() {
        parser.accept(assistant(
                tool("Read", "{\"file_path\":\"a.xlsx\"}"),
                tool("Glob", "{\"pattern\":\"*.pdf\"}"),
                tool("Grep", "{\"pattern\":\"covenant\"}"),
                tool("LS", "{\"path\":\"/home/agent/workspace\"}"),
                tool("NotebookRead", "{\"notebook_path\":\"n.ipynb\"}"),
                tool("Write", "{\"file_path\":\"out.md\",\"content\":\"x\"}"),
                tool("Bash", "{\"command\":\"ls\\nsecond line\"}")));

        assertEquals(List.of(
                new Step(StepKind.READ, "a.xlsx"),
                new Step(StepKind.READ, "glob *.pdf"),
                new Step(StepKind.READ, "grep covenant"),
                new Step(StepKind.READ, "ls /home/agent/workspace"),
                new Step(StepKind.READ, "n.ipynb"),
                new Step(StepKind.COMPUTE, "Write out.md"),
                new Step(StepKind.COMPUTE, "ls")), parser.steps());
    }

    @Test
    void aToolWithNoUsableInputStillGetsALabel() {
        parser.accept(assistant(tool("Read", "{}"), tool("Mystery", "{\"n\":1}"), tool("Bash", "{}")));
        assertEquals(List.of("Read", "Mystery", "Bash"), parser.steps().stream().map(Step::label).toList());
    }

    @Test
    void longLabelsAreCut() {
        parser.accept(assistant(tool("Bash", "{\"command\":\"" + "x".repeat(400) + "\"}")));
        String label = parser.steps().get(0).label();
        assertEquals(140, label.length());
        assertTrue(label.endsWith("…"));
    }

    @Test
    void todoBookkeepingIsNotShownAsAStep() {
        parser.accept(assistant(tool("TodoWrite", "{\"todos\":[]}"), tool("Read", "{\"file_path\":\"a\"}")));
        assertEquals(1, parser.steps().size());
    }

    @Test
    void textOnlyRunsHaveNoSteps() {
        parser.accept(assistant(text("Just an answer")));
        assertTrue(parser.steps().isEmpty());
        assertEquals("Just an answer", parser.lastAssistantText());
        assertTrue(parser.resultText().isEmpty());
    }

    @Test
    void anErrorResultIsFlagged() {
        parser.accept("{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,\"result\":\"boom\"}");
        assertTrue(parser.isError());
        assertEquals(Optional.of("boom"), parser.resultText());
    }

    @Test
    void noiseAndUnknownEventsAreIgnored() {
        parser.accept("");
        parser.accept("   ");
        parser.accept("Starting agent...");
        parser.accept("{not json");
        parser.accept("{\"type\":\"future_event\",\"x\":1}");
        parser.accept("[1,2,3]");
        parser.accept("{\"type\":\"assistant\",\"message\":{\"content\":\"not an array\"}}");
        parser.accept("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"thinking\",\"thinking\":\"hmm\"}]}}");

        assertTrue(parser.steps().isEmpty());
        assertTrue(parser.sessionId().isEmpty());
        assertTrue(parser.resultText().isEmpty());
        assertEquals("", parser.lastAssistantText());
    }
}
