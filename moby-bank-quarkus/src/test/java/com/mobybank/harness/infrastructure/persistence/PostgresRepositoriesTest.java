package com.mobybank.harness.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Message;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.SessionStatus;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.infrastructure.sbx.SandboxRecord;
import com.mobybank.harness.infrastructure.sbx.SandboxRecordStore;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** The PostgreSQL repositories against a real database (Dev Services starts it). */
@QuarkusTest
class PostgresRepositoriesTest {

    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");
    private static final AgentReply REPLY = new AgentReply(
            List.of(new Step(StepKind.READ, "a.pdf"), new Step(StepKind.COMPUTE, "ratios()")),
            List.of("Leverage rose.", "Margins fell."),
            Optional.of(new ResultTable(List.of("Metric", "Value"), List.of(List.of("Net leverage", "2.9x")))),
            Optional.of("The sandbox was started again."));

    @Inject
    SessionRepository sessions;

    @Inject
    ConnectedFoldersRepository folders;

    @Inject
    UploadStore uploads;

    @Inject
    SandboxRecordStore sandboxes;

    private static Session newSession() {
        return Session.start(SessionId.fresh(), Location.LOCAL, T0);
    }

    // --- sessions ----------------------------------------------------------------------------------------------

    @Test
    void aNewSessionCanBeSavedAndLoaded() {
        Session session = newSession();
        sessions.persist(session);

        Session loaded = sessions.findById(session.id()).orElseThrow();
        assertEquals(session.id(), loaded.id());
        assertEquals(1L, loaded.version(), "the first save advances the version from 0 to 1");
        assertEquals(Location.LOCAL, loaded.location());
        assertFalse(loaded.started());
        assertTrue(sessions.findById(SessionId.fresh()).isEmpty());
    }

    @Test
    void aConversationRoundTripsWithItsFilesStepsTableNoticeAndLocation() {
        Session session = newSession();
        session.launchAt(Location.CLOUD, T0);
        sessions.persist(session);
        Session loaded = sessions.findById(session.id()).orElseThrow();
        loaded.postUserMessage("Summarize", List.of(new FileRef("a.pdf", FileSource.ONEDRIVE),
                new FileRef("b.xlsx", FileSource.UPLOAD)), T0.plusSeconds(1));
        loaded.recordAgentReply(REPLY, T0.plusSeconds(2));
        sessions.persist(loaded);

        Session again = sessions.findById(session.id()).orElseThrow();
        assertEquals(Location.CLOUD, again.location());
        assertEquals(SessionStatus.IDLE, again.status());
        assertTrue(again.started());
        assertEquals("Summarize", again.title().value());
        assertEquals(T0.plusSeconds(2), again.updatedAt());
        assertEquals(2L, again.version());
        List<Message> messages = again.messages();
        assertEquals(2, messages.size());
        assertEquals(List.of(new FileRef("a.pdf", FileSource.ONEDRIVE), new FileRef("b.xlsx", FileSource.UPLOAD)),
                messages.get(0).files());
        Message reply = messages.get(1);
        assertEquals(REPLY.steps(), reply.steps());
        assertEquals(REPLY.paragraphs(), reply.paragraphs());
        assertEquals(REPLY.table(), reply.table());
        assertEquals(Optional.of("The sandbox was started again."), reply.notice());
        assertEquals(loaded.messages().get(1).id(), reply.id());
        assertEquals(again.filesShared(), loaded.filesShared());
    }

    @Test
    void messagesComeBackInTheOrderTheyWereWritten() {
        Session session = newSession();
        session.postUserMessage("one", List.of(), T0.plusSeconds(1));
        session.recordAgentReply(REPLY, T0.plusSeconds(2));
        session.postUserMessage("two", List.of(), T0.plusSeconds(3));
        session.recordAgentReply(REPLY, T0.plusSeconds(4));
        sessions.persist(session);

        List<String> order = sessions.findById(session.id()).orElseThrow().messages().stream()
                .map(m -> m.role() + ":" + m.text()).toList();
        assertEquals(List.of("USER:one", "ASSISTANT:", "USER:two", "ASSISTANT:"), order);
    }

    @Test
    void aMessageAddedLaterIsAppendedAndEarlierOnesAreKept() {
        Session session = newSession();
        session.postUserMessage("first", List.of(), T0.plusSeconds(1));
        sessions.persist(session);
        Session loaded = sessions.findById(session.id()).orElseThrow();
        var firstId = loaded.messages().get(0).id();
        loaded.recordAgentReply(REPLY, T0.plusSeconds(2));
        sessions.persist(loaded);

        Session again = sessions.findById(session.id()).orElseThrow();
        assertEquals(2, again.messages().size());
        assertEquals(firstId, again.messages().get(0).id());
    }

    @Test
    void callersNeverShareAMutableSession() {
        Session session = newSession();
        sessions.persist(session);

        Session first = sessions.findById(session.id()).orElseThrow();
        Session second = sessions.findById(session.id()).orElseThrow();
        assertNotSame(first, second);

        first.beginMove(Location.CLOUD, T0.plusSeconds(1)); // not saved
        assertEquals(SessionStatus.IDLE, sessions.findById(session.id()).orElseThrow().status());
    }

    @Test
    void theHistoryIsOrderedByMostRecentlyUpdated() {
        Session older = newSession();
        older.postUserMessage("older", List.of(), T0.plusSeconds(10));
        Session newer = newSession();
        newer.postUserMessage("newer", List.of(), T0.plusSeconds(20));
        sessions.persist(older);
        sessions.persist(newer);

        List<SessionId> ids = sessions.findAllByMostRecentlyUpdated().stream().map(Session::id).toList();
        assertTrue(ids.indexOf(newer.id()) >= 0 && ids.indexOf(older.id()) >= 0);
        assertTrue(ids.indexOf(newer.id()) < ids.indexOf(older.id()), "newest first: " + ids);
    }

    @Test
    void savingAgainAtAnOldVersionIsRejectedUntilTheSessionIsReloaded() {
        Session session = newSession();
        sessions.persist(session);
        Session loaded = sessions.findById(session.id()).orElseThrow();
        loaded.postUserMessage("hello", List.of(), T0.plusSeconds(1));
        sessions.persist(loaded); // the database is now at version 2

        assertThrows(StaleAggregateException.class, () -> sessions.persist(session), "still holds version 0");
        assertThrows(StaleAggregateException.class, () -> sessions.persist(loaded),
                "loaded at version 1; a save advances the database, so reload before changing it again");

        Session reloaded = sessions.findById(session.id()).orElseThrow();
        assertEquals(2L, reloaded.version());
        reloaded.recordAgentReply(REPLY, T0.plusSeconds(2));
        sessions.persist(reloaded);
        assertEquals(3L, sessions.findById(session.id()).orElseThrow().version());
    }

    @Test
    void aNewSessionSavedTwiceAtVersionZeroIsRejected() {
        Session session = newSession();
        sessions.persist(session);

        assertThrows(StaleAggregateException.class, () -> sessions.persist(session));
    }

    @Test
    void ofTwoWritersAtTheSameVersionExactlyOneWins() throws Exception {
        Session session = newSession();
        sessions.persist(session);
        Session a = sessions.findById(session.id()).orElseThrow();
        Session b = sessions.findById(session.id()).orElseThrow();
        a.postUserMessage("from a", List.of(), T0.plusSeconds(1));
        b.postUserMessage("from b", List.of(), T0.plusSeconds(1));

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> writers = List.of(() -> save(a, go), () -> save(b, go));
            List<Future<Boolean>> results = new ArrayList<>();
            writers.forEach(w -> results.add(pool.submit(w)));
            go.countDown();
            long winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get() ? 1 : 0;
            }
            assertEquals(1, winners, "one save succeeds and the other is stale");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2L, sessions.findById(session.id()).orElseThrow().version());
    }

    private boolean save(Session session, CountDownLatch go) throws InterruptedException {
        go.await();
        try {
            sessions.persist(session);
            return true;
        } catch (StaleAggregateException e) {
            return false;
        }
    }

    // --- connected folders -------------------------------------------------------------------------------------

    @Test
    void connectedFoldersKeepTheirOrderAndVersion() {
        UserId user = new UserId("user-" + SessionId.fresh().value());
        ConnectedFolders connected = ConnectedFolders.none(user);
        connected.connect(List.of(new FolderId("f3"), new FolderId("f1")));
        folders.persist(connected);

        ConnectedFolders loaded = folders.findByUser(user).orElseThrow();
        assertEquals(List.of(new FolderId("f3"), new FolderId("f1")), loaded.folderIds());
        assertEquals(1L, loaded.version());
        loaded.connect(List.of(new FolderId("f2")));
        folders.persist(loaded);
        assertEquals(List.of(new FolderId("f3"), new FolderId("f1"), new FolderId("f2")),
                folders.findByUser(user).orElseThrow().folderIds());
        assertThrows(StaleAggregateException.class, () -> folders.persist(connected));
        assertTrue(folders.findByUser(new UserId("nobody")).isEmpty());
    }

    // --- uploads -----------------------------------------------------------------------------------------------

    @Test
    void anUploadComesBackByteForByteAndStoringTheNameAgainReplacesIt() {
        SessionId session = SessionId.fresh();
        byte[] content = {0, 1, 2, (byte) 255, 10};
        uploads.store(session, "notes.pdf", content);

        assertTrue(java.util.Arrays.equals(content, uploads.find(session, "notes.pdf").orElseThrow()));
        assertTrue(uploads.find(session, "other.pdf").isEmpty());
        assertTrue(uploads.find(SessionId.fresh(), "notes.pdf").isEmpty(), "uploads belong to one session");

        uploads.store(session, "notes.pdf", new byte[] {9});
        assertTrue(java.util.Arrays.equals(new byte[] {9}, uploads.find(session, "notes.pdf").orElseThrow()));
    }

    // --- sandbox records ---------------------------------------------------------------------------------------

    @Test
    void aSandboxRecordRoundTripsWithItsHandleAgentConversationAndPendingFiles() {
        SessionId session = SessionId.fresh();
        assertTrue(sandboxes.find(session).isEmpty());

        sandboxes.save(session, new SandboxRecord(new Handle(Location.CLOUD, "moby-bank-0a1b2c3d4e5f-g1-zz", 1),
                "agent-7", true));
        SandboxRecord found = sandboxes.find(session).orElseThrow();
        assertEquals(new Handle(Location.CLOUD, "moby-bank-0a1b2c3d4e5f-g1-zz", 1), found.handle());
        assertEquals("agent-7", found.agentSession());
        assertTrue(found.awaitingFiles());

        sandboxes.save(session, new SandboxRecord(null, null, false));
        SandboxRecord cleared = sandboxes.find(session).orElseThrow();
        assertEquals(null, cleared.handle());
        assertEquals(null, cleared.agentSession());
        assertFalse(cleared.awaitingFiles());
    }
}
