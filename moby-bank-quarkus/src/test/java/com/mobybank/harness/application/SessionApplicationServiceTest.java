package com.mobybank.harness.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.Fakes.QueueRunner;
import com.mobybank.harness.application.Fakes.RecordingEvents;
import com.mobybank.harness.application.Fakes.ScriptedAgent;
import com.mobybank.harness.application.Fakes.ScriptedTransfer;
import com.mobybank.harness.application.Fakes.SessionRepo;
import com.mobybank.harness.application.Fakes.Uploads;
import com.mobybank.harness.application.SessionEvent.MessageAdded;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEvent.MoveFailed;
import com.mobybank.harness.application.SessionEvent.Thinking;
import com.mobybank.harness.domain.InvalidMoveException;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionBusyException;
import com.mobybank.harness.domain.SessionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    private SessionRepo sessions;
    private ScriptedAgent agent;
    private ScriptedTransfer transfer;
    private Uploads uploads;
    private RecordingEvents events;
    private QueueRunner runner;
    private SessionApplicationService service;

    @BeforeEach
    void setUp() {
        sessions = new SessionRepo();
        agent = new ScriptedAgent();
        transfer = new ScriptedTransfer();
        uploads = new Uploads();
        events = new RecordingEvents();
        runner = new QueueRunner();
        service = new SessionApplicationService(sessions, agent, transfer, uploads, events, runner,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- create / list / get -----------------------------------------------------------------------------------

    @Test
    void createDefaultsToALocalIdleSession() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        assertEquals("local", created.location());
        assertEquals("idle", created.status());
        assertEquals("New conversation", created.title());
        assertEquals("Today", created.group());
        assertTrue(created.messages().isEmpty());
        assertEquals(created.id(), service.get(created.id()).id());
    }

    @Test
    void createCanStartInTheCloud() {
        assertEquals("cloud", service.create(new CreateSessionCommand("cloud")).location());
        assertEquals("cloud", service.create(new CreateSessionCommand("CLOUD")).location());
    }

    @Test
    void createRejectsAnUnknownLocation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateSessionCommand("mars")));
    }

    @Test
    void listIsMostRecentFirstAndGroupedByRecency() {
        sessions.persist(Session.start(SessionId.fresh(), Location.LOCAL, NOW.minusSeconds(30L * 24 * 3600)));
        sessions.persist(Session.start(SessionId.fresh(), Location.CLOUD, NOW));
        sessions.persist(Session.start(SessionId.fresh(), Location.LOCAL, NOW.minusSeconds(3L * 24 * 3600)));

        List<SessionSummaryDTO> list = service.list();

        assertEquals(List.of("Today", "Previous 7 days", "Earlier"), list.stream().map(SessionSummaryDTO::group).toList());
        assertEquals("cloud", list.get(0).location());
    }

    @Test
    void getRejectsUnknownAndMalformedIds() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(SessionId.fresh().value().toString()));
        assertThrows(IllegalArgumentException.class, () -> service.get("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> service.get(" "));
    }

    // --- send message ------------------------------------------------------------------------------------------

    @Test
    void sendMessageStoresItStartsTheAgentAndReturnsImmediately() {
        SessionDTO created = service.create(new CreateSessionCommand(null));

        MessageDTO stored = service.sendMessage(new SendMessageCommand(created.id(), "How does 2.9x compare?", List.of()));

        assertEquals("user", stored.role());
        assertEquals("How does 2.9x compare?", stored.text());
        SessionDTO running = service.get(created.id());
        assertEquals("running", running.status());
        assertEquals("How does 2.9x compare?", running.title());
        assertEquals(List.of("message", "thinking"), events.types());
        assertEquals("Working on local model…", ((Thinking) events.published.get(1)).label());
        assertEquals(1, runner.pending());
        assertTrue(agent.turns.isEmpty(), "the agent must not run on the caller's thread");
    }

    @Test
    void theAgentReplyArrivesAsStepsThenAMessage() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.sendMessage(new SendMessageCommand(created.id(), "question", List.of()));

        runner.drain();

        assertEquals(List.of("message", "thinking", "step", "step", "message"), events.types());
        MessageAdded reply = assertInstanceOf(MessageAdded.class, events.published.get(4));
        assertEquals("assistant", reply.message().role());
        assertEquals(List.of("Leverage rose to 2.9x."), reply.message().paragraphs());
        assertEquals("read", reply.message().steps().get(0).kind());
        assertNotNull(reply.message().table());

        SessionDTO done = service.get(created.id());
        assertEquals("idle", done.status());
        assertEquals(2, done.messages().size());
    }

    @Test
    void theAgentSeesTheHistoryBeforeTheNewMessage() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.sendMessage(new SendMessageCommand(created.id(), "first", List.of()));
        runner.drain();
        service.sendMessage(new SendMessageCommand(created.id(), "second", List.of()));
        runner.drain();

        assertEquals(2, agent.turns.size());
        assertEquals(0, agent.turns.get(0).history().size());
        assertEquals(2, agent.turns.get(1).history().size());
        assertEquals("second", agent.turns.get(1).userMessage().text());
        assertEquals(Location.LOCAL, agent.turns.get(1).location());
    }

    @Test
    void attachedFilesShapeTheThinkingLabelAndTheTitle() {
        SessionDTO created = service.create(new CreateSessionCommand("cloud"));

        MessageDTO stored = service.sendMessage(new SendMessageCommand(created.id(), "  ", List.of(
                new FileRefDTO("Fathom_Q2_2026_10-Q.pdf", "onedrive"), new FileRefDTO("notes.docx", "upload"))));

        assertEquals("Review the attached files.", stored.text());
        assertEquals(List.of("onedrive", "upload"), stored.files().stream().map(FileRefDTO::source).toList());
        assertEquals("Reading 2 files on cloud model…", ((Thinking) events.published.get(1)).label());
        assertEquals("Fathom_Q2_2026_10-Q.pdf", service.get(created.id()).title());
    }

    @Test
    void aSecondMessageWhileTheAgentIsWorkingIsRefused() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.sendMessage(new SendMessageCommand(created.id(), "one", List.of()));

        assertThrows(SessionBusyException.class,
                () -> service.sendMessage(new SendMessageCommand(created.id(), "two", List.of())));

        assertEquals(1, runner.pending());
        assertEquals(1, service.get(created.id()).messages().size());
    }

    @Test
    void anEmptyMessageIsRejected() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMessage(new SendMessageCommand(created.id(), null, null)));
        assertEquals("idle", service.get(created.id()).status());
        assertEquals(0, runner.pending());
    }

    @Test
    void anUnknownFileSourceIsRejected() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(
                new SendMessageCommand(created.id(), "hi", List.of(new FileRefDTO("a.pdf", "dropbox")))));
    }

    @Test
    void sendingToAnUnknownSessionIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.sendMessage(
                new SendMessageCommand(SessionId.fresh().value().toString(), "hi", List.of())));
    }

    @Test
    void anAgentFailureIsShownToTheAnalystAndFreesTheSession() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        agent.failure = new com.mobybank.harness.domain.SandboxFailureException("sandbox unreachable");
        service.sendMessage(new SendMessageCommand(created.id(), "question", List.of()));

        runner.drain();

        SessionDTO after = service.get(created.id());
        assertEquals("idle", after.status());
        MessageDTO failure = after.messages().get(1);
        assertEquals("assistant", failure.role());
        assertTrue(failure.paragraphs().get(0).contains("sandbox unreachable"));
        assertEquals("message", events.types().get(events.types().size() - 1));

        agent.failure = null;
        service.sendMessage(new SendMessageCommand(created.id(), "try again", List.of()));
        runner.drain();
        assertEquals(4, service.get(created.id()).messages().size());
    }

    @Test
    void anUnexpectedAgentErrorIsHandledTheSameWay() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        agent.failure = new IllegalStateException("boom");
        service.sendMessage(new SendMessageCommand(created.id(), "question", List.of()));

        runner.drain();

        assertEquals("idle", service.get(created.id()).status());
        assertTrue(service.get(created.id()).messages().get(1).paragraphs().get(0).contains("boom"));
    }

    // --- moves -------------------------------------------------------------------------------------------------

    @Test
    void movesALocalSessionToTheCloud() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.sendMessage(new SendMessageCommand(created.id(), "question",
                List.of(new FileRefDTO("a.pdf", "onedrive"))));
        runner.drain();
        events.published.clear();

        SessionDTO moving = service.move(new MoveSessionCommand(created.id(), "cloud"));

        assertEquals("moving", moving.status());
        assertEquals("cloud", moving.moveTarget());
        assertEquals("local", moving.location());
        assertTrue(transfer.requests.isEmpty(), "the transfer must not run on the caller's thread");

        runner.drain();

        assertEquals(List.of("move-progress", "move-progress", "moved"), events.types());
        assertEquals("cloud", ((MoveCompleted) events.published.get(2)).location());
        SessionDTO moved = service.get(created.id());
        assertEquals("cloud", moved.location());
        assertEquals("idle", moved.status());
        assertNull(moved.moveTarget());

        var request = transfer.requests.get(0);
        assertEquals(Location.LOCAL, request.from());
        assertEquals(Location.CLOUD, request.to());
        assertEquals(2, request.history().size());
        assertEquals(1, request.files().size());
    }

    @Test
    void movesACloudSessionBackToLocal() {
        SessionDTO created = service.create(new CreateSessionCommand("cloud"));

        service.move(new MoveSessionCommand(created.id(), "local"));
        runner.drain();

        assertEquals("local", service.get(created.id()).location());
        assertEquals("local", ((MoveCompleted) events.published.get(events.published.size() - 1)).location());
    }

    @Test
    void aSessionCanGoThereAndBack() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.move(new MoveSessionCommand(created.id(), "cloud"));
        runner.drain();
        service.move(new MoveSessionCommand(created.id(), "local"));
        runner.drain();
        assertEquals("local", service.get(created.id()).location());
        assertEquals(2, transfer.requests.size());
    }

    @Test
    void movingToTheCurrentLocationIsRefused() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        assertThrows(InvalidMoveException.class, () -> service.move(new MoveSessionCommand(created.id(), "local")));
        assertEquals(0, runner.pending());
        assertEquals("idle", service.get(created.id()).status());
    }

    @Test
    void aMoveNeedsATarget() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        assertThrows(IllegalArgumentException.class, () -> service.move(new MoveSessionCommand(created.id(), null)));
    }

    @Test
    void nothingElseHappensToASessionWhileItMoves() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.move(new MoveSessionCommand(created.id(), "cloud"));

        assertThrows(SessionBusyException.class,
                () -> service.sendMessage(new SendMessageCommand(created.id(), "hi", List.of())));
        assertThrows(SessionBusyException.class, () -> service.move(new MoveSessionCommand(created.id(), "local")));
        assertEquals(1, runner.pending());
    }

    @Test
    void aMessageIsRefusedWhileTheAgentRunsAndAMoveToo() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        service.sendMessage(new SendMessageCommand(created.id(), "question", List.of()));
        assertThrows(SessionBusyException.class, () -> service.move(new MoveSessionCommand(created.id(), "cloud")));
    }

    @Test
    void aFailedMoveLeavesTheSessionWhereItWasAndSaysSo() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        transfer.failure = new com.mobybank.harness.domain.SandboxFailureException("private link down");
        service.move(new MoveSessionCommand(created.id(), "cloud"));

        runner.drain();

        SessionDTO after = service.get(created.id());
        assertEquals("local", after.location());
        assertEquals("idle", after.status());
        assertNull(after.moveTarget());
        MoveFailed failed = assertInstanceOf(MoveFailed.class, events.published.get(events.published.size() - 1));
        assertTrue(failed.reason().contains("private link down"));

        transfer.failure = null;
        service.move(new MoveSessionCommand(created.id(), "cloud"));
        runner.drain();
        assertEquals("cloud", service.get(created.id()).location());
    }

    // --- uploads -----------------------------------------------------------------------------------------------

    @Test
    void anUploadIsStoredForTheSession() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        byte[] content = {1, 2, 3};

        FileRefDTO ref = service.attachUpload(new AttachUploadCommand(created.id(), "  notes.docx ", content));

        assertEquals(new FileRefDTO("notes.docx", "upload"), ref);
        assertArrayEquals(content, uploads.find(SessionId.parse(created.id()), "notes.docx").orElseThrow());
    }

    @Test
    void uploadsNeedAKnownSessionANameAndContent() {
        SessionDTO created = service.create(new CreateSessionCommand(null));
        assertThrows(ResourceNotFoundException.class, () -> service.attachUpload(
                new AttachUploadCommand(SessionId.fresh().value().toString(), "a.txt", new byte[0])));
        assertThrows(IllegalArgumentException.class,
                () -> service.attachUpload(new AttachUploadCommand(created.id(), " ", new byte[0])));
        assertThrows(IllegalArgumentException.class,
                () -> service.attachUpload(new AttachUploadCommand(created.id(), "a.txt", null)));
        assertTrue(uploads.files.isEmpty());
    }
}
