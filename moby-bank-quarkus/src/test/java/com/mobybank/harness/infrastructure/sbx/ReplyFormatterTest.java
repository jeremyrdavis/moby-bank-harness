package com.mobybank.harness.infrastructure.sbx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplyFormatterTest {

    private static AgentReply format(String text) {
        return ReplyFormatter.toReply(text, List.of());
    }

    @Test
    void blankLinesSeparateParagraphs() {
        AgentReply reply = format("First paragraph.\n\nSecond paragraph\nstill second.\n\n\n  \nThird.");
        assertEquals(List.of("First paragraph.", "Second paragraph\nstill second.", "Third."), reply.paragraphs());
        assertTrue(reply.table().isEmpty());
    }

    @Test
    void theFirstMarkdownTableBecomesAStructuredTable() {
        AgentReply reply = format("""
                Leverage rose after the acquisition.

                | Metric | Q2 2026 | Q2 2025 |
                |---|---|---|
                | Revenue | $4.12B | $3.87B |
                | Net leverage | 2.9x | 2.4x |

                That is above the peer median.""");

        ResultTable table = reply.table().orElseThrow();
        assertEquals(List.of("Metric", "Q2 2026", "Q2 2025"), table.cols());
        assertEquals(List.of(List.of("Revenue", "$4.12B", "$3.87B"), List.of("Net leverage", "2.9x", "2.4x")), table.rows());
        assertEquals(List.of("Leverage rose after the acquisition.", "That is above the peer median."), reply.paragraphs());
    }

    @Test
    void alignmentMarkersAndMissingEdgePipesAreAccepted() {
        ResultTable table = format("Metric | Value\n:--- | ---:\nEBITDA margin | 17.4%").table().orElse(null);
        assertEquals(null, table, "a table needs leading and trailing pipes to be recognised");

        ResultTable piped = format("| Metric | Value |\n| :--- | ---: |\n| EBITDA margin | 17.4% |").table().orElseThrow();
        assertEquals(List.of(List.of("EBITDA margin", "17.4%")), piped.rows());
    }

    @Test
    void rowsAreFittedToTheHeaderWidth() {
        ResultTable table = format("| A | B | C |\n|---|---|---|\n| 1 |\n| 1 | 2 | 3 | 4 |").table().orElseThrow();
        assertEquals(List.of(List.of("1", "", ""), List.of("1", "2", "3")), table.rows());
    }

    @Test
    void onlyTheFirstTableIsExtracted() {
        AgentReply reply = format("| A |\n|---|\n| 1 |\n\nBetween.\n\n| B |\n|---|\n| 2 |");
        assertEquals(List.of("A"), reply.table().orElseThrow().cols());
        assertTrue(reply.paragraphs().get(1).contains("| B |"), reply.paragraphs().toString());
    }

    @Test
    void aTableHeaderWithoutASeparatorStaysText() {
        AgentReply reply = format("| not | a table |\nbecause there is no separator row");
        assertTrue(reply.table().isEmpty());
        assertEquals(1, reply.paragraphs().size());
    }

    @Test
    void anEmptyAnswerGetsAPlaceholderSoTheReplyIsValid() {
        assertEquals(List.of(ReplyFormatter.EMPTY_ANSWER), format("").paragraphs());
        assertEquals(List.of(ReplyFormatter.EMPTY_ANSWER), format("  \n \n").paragraphs());
        assertEquals(List.of(ReplyFormatter.EMPTY_ANSWER), format("| A |\n|---|\n| 1 |").paragraphs());
    }

    @Test
    void stepsArePassedThrough() {
        List<Step> steps = List.of(new Step(StepKind.READ, "a.pdf"));
        assertEquals(steps, ReplyFormatter.toReply("Done.", steps).steps());
    }
}
