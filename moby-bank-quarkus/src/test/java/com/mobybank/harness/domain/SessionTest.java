package com.mobybank.harness.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);

    private static final AgentReply REPLY = new AgentReply(
            List.of(new Step(StepKind.READ, "a.pdf"), new Step(StepKind.COMPUTE, "leverage(net_debt, ebitda)")),
            List.of("Leverage rose."),
            Optional.of(new ResultTable(List.of("Metric", "Value"), List.of(List.of("Net leverage", "2.9x")))));

    private static Session newSession(Location location) {
        return Session.start(SessionId.fresh(), location, T0);
    }

    // --- start -------------------------------------------------------------------------------------------------

    @Test
    void startsIdleAndEmptyWithDefaultTitle() {
        Session session = newSession(Location.LOCAL);
        assertEquals(SessionStatus.IDLE, session.status());
        assertEquals(Location.LOCAL, session.location());
        assertEquals(SessionTitle.NEW_CONVERSATION, session.title());
        assertTrue(session.messages().isEmpty());
        assertNull(session.moveTarget());
        assertEquals(0L, session.version());
        assertEquals(T0, session.updatedAt());
    }

    // --- user messages -----------------------------------------------------------------------------------------

    @Test
    void firstMessageNamesTheSessionAndStartsATurn() {
        Session session = newSession(Location.LOCAL);
        Message message = session.postUserMessage("  How does 2.9x compare with peers?  ", List.of(), T1);

        assertEquals(MessageRole.USER, message.role());
        assertEquals("How does 2.9x compare with peers?", message.text());
        assertEquals("How does 2.9x compare with peers?", session.title().value());
        assertEquals(SessionStatus.RUNNING, session.status());
        assertEquals(T1, session.updatedAt());
    }

    @Test
    void longFirstMessageGivesA48CharacterTitle() {
        Session session = newSession(Location.LOCAL);
        session.postUserMessage("x".repeat(100), List.of(), T1);
        assertEquals(48, session.title().value().length());
    }

    @Test
    void filesOnlyMessageGetsDefaultPromptAndFileNameTitle() {
        Session session = newSession(Location.LOCAL);
        Message message = session.postUserMessage("   ",
                List.of(new FileRef("Fathom_Q2_2026_10-Q.pdf", FileSource.ONEDRIVE)), T1);

        assertEquals("Review the attached files.", message.text());
        assertEquals("Fathom_Q2_2026_10-Q.pdf", session.title().value());
    }

    @Test
    void emptyMessageWithoutFilesIsRejected() {
        Session session = newSession(Location.LOCAL);
        assertThrows(IllegalArgumentException.class, () -> session.postUserMessage(" ", List.of(), T1));
        assertEquals(SessionStatus.IDLE, session.status());
    }

    @Test
    void laterMessagesDoNotRenameTheSession() {
        Session session = newSession(Location.LOCAL);
        session.postUserMessage("First question", List.of(), T1);
        session.recordAgentReply(REPLY, T1);
        session.postUserMessage("Second question", List.of(), T2);
        assertEquals("First question", session.title().value());
    }

    @Test
    void cannotPostWhileRunning() {
        Session session = newSession(Location.LOCAL);
        session.postUserMessage("one", List.of(), T1);
        SessionBusyException busy = assertThrows(SessionBusyException.class,
                () -> session.postUserMessage("two", List.of(), T2));
        assertEquals(SessionStatus.RUNNING, busy.status());
        assertEquals(1, session.messages().size());
    }

    // --- agent replies -----------------------------------------------------------------------------------------

    @Test
    void agentReplyIsRecordedAndFreesTheSession() {
        Session session = newSession(Location.LOCAL);
        session.postUserMessage("question", List.of(), T1);
        Message reply = session.recordAgentReply(REPLY, T2);

        assertEquals(MessageRole.ASSISTANT, reply.role());
        assertEquals(2, reply.steps().size());
        assertEquals(List.of("Leverage rose."), reply.paragraphs());
        assertTrue(reply.table().isPresent());
        assertEquals(SessionStatus.IDLE, session.status());
        assertEquals(T2, session.updatedAt());
        assertEquals(List.of(new AgentRepliedEvent(session.id(), reply.id())), session.pullPendingEvents());
    }

    @Test
    void replyWithoutARunningTurnIsAProgrammingError() {
        Session session = newSession(Location.LOCAL);
        assertThrows(IllegalStateException.class, () -> session.recordAgentReply(REPLY, T1));
    }

    @Test
    void agentFailureExplainsWhyAndFreesTheSession() {
        Session session = newSession(Location.CLOUD);
        session.postUserMessage("question", List.of(), T1);
        Message failure = session.recordAgentFailure("sandbox unreachable", T2);

        assertEquals(SessionStatus.IDLE, session.status());
        assertTrue(failure.paragraphs().get(0).contains("sandbox unreachable"));
        assertTrue(session.pullPendingEvents().isEmpty());
    }

    // --- moves -------------------------------------------------------------------------------------------------

    @Test
    void movesLocalToCloud() {
        Session session = newSession(Location.LOCAL);
        session.beginMove(Location.CLOUD, T1);
        assertEquals(SessionStatus.MOVING, session.status());
        assertEquals(Location.CLOUD, session.moveTarget());
        assertEquals(Location.LOCAL, session.location());

        session.completeMove(T2);
        assertEquals(Location.CLOUD, session.location());
        assertEquals(SessionStatus.IDLE, session.status());
        assertNull(session.moveTarget());
        assertEquals(List.of(new SessionMovedEvent(session.id(), Location.LOCAL, Location.CLOUD)),
                session.pullPendingEvents());
    }

    @Test
    void movesCloudToLocal() {
        Session session = newSession(Location.CLOUD);
        session.beginMove(Location.LOCAL, T1);
        session.completeMove(T2);
        assertEquals(Location.LOCAL, session.location());
        assertEquals(List.of(new SessionMovedEvent(session.id(), Location.CLOUD, Location.LOCAL)),
                session.pullPendingEvents());
    }

    @Test
    void canMoveThereAndBack() {
        Session session = newSession(Location.LOCAL);
        session.beginMove(Location.CLOUD, T1);
        session.completeMove(T1);
        session.beginMove(Location.LOCAL, T2);
        session.completeMove(T2);
        assertEquals(Location.LOCAL, session.location());
        assertEquals(2, session.pullPendingEvents().size());
    }

    @Test
    void movingToTheCurrentLocationIsRejected() {
        Session session = newSession(Location.LOCAL);
        assertThrows(InvalidMoveException.class, () -> session.beginMove(Location.LOCAL, T1));
        assertEquals(SessionStatus.IDLE, session.status());
    }

    @Test
    void cannotMoveWhileRunning() {
        Session session = newSession(Location.LOCAL);
        session.postUserMessage("question", List.of(), T1);
        assertThrows(SessionBusyException.class, () -> session.beginMove(Location.CLOUD, T2));
        assertEquals(Location.LOCAL, session.location());
    }

    @Test
    void cannotMoveTwiceAtOnce() {
        Session session = newSession(Location.LOCAL);
        session.beginMove(Location.CLOUD, T1);
        assertThrows(SessionBusyException.class, () -> session.beginMove(Location.LOCAL, T2));
    }

    @Test
    void cannotPostWhileMoving() {
        Session session = newSession(Location.LOCAL);
        session.beginMove(Location.CLOUD, T1);
        SessionBusyException busy = assertThrows(SessionBusyException.class,
                () -> session.postUserMessage("hi", List.of(), T2));
        assertEquals(SessionStatus.MOVING, busy.status());
    }

    @Test
    void abortedMoveLeavesTheSessionWhereItWas() {
        Session session = newSession(Location.LOCAL);
        session.beginMove(Location.CLOUD, T1);
        session.abortMove(T2);
        assertEquals(Location.LOCAL, session.location());
        assertEquals(SessionStatus.IDLE, session.status());
        assertNull(session.moveTarget());
        assertTrue(session.pullPendingEvents().isEmpty());
    }

    @Test
    void completeOrAbortWithoutAMoveIsAProgrammingError() {
        Session session = newSession(Location.LOCAL);
        assertThrows(IllegalStateException.class, () -> session.completeMove(T1));
        assertThrows(IllegalStateException.class, () -> session.abortMove(T1));
    }

    // --- reads and rehydration ---------------------------------------------------------------------------------

    @Test
    void filesSharedAreDistinctAndInOrder() {
        Session session = newSession(Location.LOCAL);
        FileRef a = new FileRef("a.pdf", FileSource.ONEDRIVE);
        FileRef b = new FileRef("b.xlsx", FileSource.UPLOAD);
        session.postUserMessage("one", List.of(a, b), T1);
        session.recordAgentReply(REPLY, T1);
        session.postUserMessage("two", List.of(a), T2);
        assertEquals(List.of(a, b), session.filesShared());
    }

    @Test
    void messagesAreReturnedAsACopy() {
        Session session = newSession(Location.LOCAL);
        session.postUserMessage("one", List.of(), T1);
        assertThrows(UnsupportedOperationException.class, () -> session.messages().clear());
    }

    @Test
    void pendingEventsAreDrainedOnce() {
        Session session = newSession(Location.LOCAL);
        session.beginMove(Location.CLOUD, T1);
        session.completeMove(T2);
        assertEquals(1, session.pullPendingEvents().size());
        assertTrue(session.pullPendingEvents().isEmpty());
    }

    @Test
    void rehydrateRestoresStateWithoutRaisingEvents() {
        Session original = newSession(Location.CLOUD);
        original.postUserMessage("question", List.of(), T1);
        original.recordAgentReply(REPLY, T2);
        original.pullPendingEvents();

        Session restored = Session.rehydrate(original.id(), original.title(), original.location(), original.status(),
                original.moveTarget(), original.messages(), original.createdAt(), original.updatedAt(), 7L);

        assertEquals(original.id(), restored.id());
        assertEquals(Location.CLOUD, restored.location());
        assertEquals(2, restored.messages().size());
        assertEquals(7L, restored.version());
        assertTrue(restored.pullPendingEvents().isEmpty());
    }
}
