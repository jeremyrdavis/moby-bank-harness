package com.mobybank.harness.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    void typedIdsRejectNull() {
        assertThrows(NullPointerException.class, () -> new SessionId(null));
        assertThrows(NullPointerException.class, () -> new MessageId(null));
    }

    @Test
    void typedIdsWithSameValueAreEqual() {
        UUID uuid = UUID.randomUUID();
        assertEquals(new SessionId(uuid), new SessionId(uuid));
        assertEquals(new SessionId(uuid), SessionId.parse(uuid.toString()));
    }

    @Test
    void folderAndUserIdsAreTrimmedAndRejectBlank() {
        assertEquals("f1", new FolderId("  f1 ").value());
        assertThrows(IllegalArgumentException.class, () -> new FolderId("  "));
        assertThrows(IllegalArgumentException.class, () -> new UserId(""));
    }

    @Test
    void fileRefTrimsNameAndRejectsBlank() {
        assertEquals("a.pdf", new FileRef(" a.pdf ", FileSource.UPLOAD).name());
        assertThrows(IllegalArgumentException.class, () -> new FileRef(" ", FileSource.UPLOAD));
        assertThrows(NullPointerException.class, () -> new FileRef("a.pdf", null));
    }

    @Test
    void stepRequiresKindAndLabel() {
        assertThrows(NullPointerException.class, () -> new Step(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Step(StepKind.READ, " "));
    }

    @Test
    void resultTableRowsMustMatchColumnCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultTable(List.of("Metric", "Value"), List.of(List.of("Revenue"))));
        assertThrows(IllegalArgumentException.class, () -> new ResultTable(List.of(), List.of()));
        ResultTable table = new ResultTable(List.of("Metric", "Value"), List.of(List.of("Revenue", "$4.12B")));
        assertEquals(1, table.rows().size());
    }

    @Test
    void resultTableIsDefensivelyCopied() {
        List<String> cols = new java.util.ArrayList<>(List.of("A"));
        ResultTable table = new ResultTable(cols, List.of(List.of("1")));
        cols.add("B");
        assertEquals(List.of("A"), table.cols());
        assertThrows(UnsupportedOperationException.class, () -> table.cols().add("C"));
    }

    @Test
    void sessionTitleIsTrimmedAndCutTo48Characters() {
        assertEquals("Hello", new SessionTitle("  Hello  ").value());
        String longText = "x".repeat(60);
        assertEquals(48, new SessionTitle(longText).value().length());
        assertThrows(IllegalArgumentException.class, () -> new SessionTitle("   "));
    }

    @Test
    void sessionTitleDoesNotSplitSurrogatePairs() {
        String emoji = "😀".repeat(60);
        SessionTitle title = new SessionTitle(emoji);
        assertEquals(48, title.value().codePointCount(0, title.value().length()));
    }

    @Test
    void agentReplyNeedsAParagraph() {
        assertThrows(IllegalArgumentException.class, () -> new AgentReply(List.of(), List.of(), Optional.empty()));
        AgentReply reply = new AgentReply(List.of(), List.of("Done."), Optional.empty());
        assertTrue(reply.table().isEmpty());
    }

    @Test
    void locationOtherFlips() {
        assertEquals(Location.CLOUD, Location.LOCAL.other());
        assertEquals(Location.LOCAL, Location.CLOUD.other());
    }

    @Test
    void moveRequestRejectsSameSourceAndTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new MoveRequest(SessionId.fresh(), Location.LOCAL, Location.LOCAL, List.of(), List.of()));
    }

    @Test
    void agentTurnMustStartFromUserMessage() {
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, java.time.Instant.EPOCH);
        session.postUserMessage("hi", List.of(), java.time.Instant.EPOCH);
        session.recordAgentReply(new AgentReply(List.of(), List.of("hello"), Optional.empty()),
                java.time.Instant.EPOCH);
        Message assistant = session.messages().get(1);
        assertThrows(IllegalArgumentException.class,
                () -> new AgentTurn(session.id(), Location.LOCAL, List.of(), assistant));
    }

    @Test
    void folderRejectsNegativeFileCount() {
        assertThrows(IllegalArgumentException.class, () -> new Folder(new FolderId("f1"), "Earnings", "a/b", -1));
    }
}
