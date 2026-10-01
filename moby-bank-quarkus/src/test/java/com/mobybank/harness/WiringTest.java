package com.mobybank.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.CreateSessionCommand;
import com.mobybank.harness.application.CurrentUserApplicationService;
import com.mobybank.harness.application.FileRefDTO;
import com.mobybank.harness.application.FolderApplicationService;
import com.mobybank.harness.application.FolderDTO;
import com.mobybank.harness.application.MoveSessionCommand;
import com.mobybank.harness.application.SendMessageCommand;
import com.mobybank.harness.application.SessionApplicationService;
import com.mobybank.harness.application.SessionDTO;
import com.mobybank.harness.application.SessionEvent;
import com.mobybank.harness.application.SessionEvent.MessageAdded;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEventStream;
import com.mobybank.harness.application.SessionSummaryDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Runs the real beans together inside Quarkus: CDI wiring, config-selected fakes, seeding and background work. */
@QuarkusTest
class WiringTest {

    @Inject
    SessionApplicationService sessions;

    @Inject
    FolderApplicationService folders;

    @Inject
    CurrentUserApplicationService user;

    @Inject
    SessionEventStream events;

    @Test
    void theDemoHistoryIsSeededAtStartup() {
        List<String> titles = sessions.list().stream().map(SessionSummaryDTO::title).toList();
        assertTrue(titles.contains("Fathom Industrial — Q2 2026 earnings"), titles.toString());
        assertTrue(titles.contains("Segment revenue bridge FY25 → FY26"), titles.toString());
    }

    @Test
    void theFirstThreeFoldersAreConnected() {
        List<String> connected = folders.connected().stream().map(FolderDTO::id).toList();
        assertTrue(connected.containsAll(List.of("f1", "f2", "f3")), connected.toString());
        assertEquals(6, folders.library().size());
    }

    @Test
    void theConfiguredAnalystIsServed() {
        assertEquals("Hermione Granger", user.me().name());
        assertEquals("HG", user.me().initials());
    }

    @Test
    void aMessageRunsThroughTheRealBeansAndTheFakeAgent() throws InterruptedException {
        SessionDTO session = sessions.create(new CreateSessionCommand("local"));
        List<String> types = new CopyOnWriteArrayList<>();

        awaitEvent(session.id(), types,
                e -> e instanceof MessageAdded added && "assistant".equals(added.message().role()),
                () -> sessions.sendMessage(new SendMessageCommand(session.id(), "Summarize the filing",
                        List.of(new FileRefDTO("Fathom_Q2_2026_10-Q.pdf", "onedrive")))));

        SessionDTO after = sessions.get(session.id());
        assertEquals("idle", after.status());
        assertEquals(2, after.messages().size());
        assertEquals("assistant", after.messages().get(1).role());
        assertTrue(types.contains("thinking") && types.contains("step"), types.toString());
    }

    @Test
    void aSessionMovesToTheCloudAndBack() throws InterruptedException {
        SessionDTO session = sessions.create(new CreateSessionCommand("local"));
        List<String> types = new CopyOnWriteArrayList<>();

        awaitEvent(session.id(), types, e -> e instanceof MoveCompleted,
                () -> sessions.move(new MoveSessionCommand(session.id(), "cloud")));
        assertEquals("cloud", sessions.get(session.id()).location());

        awaitEvent(session.id(), types, e -> e instanceof MoveCompleted done && "local".equals(done.location()),
                () -> sessions.move(new MoveSessionCommand(session.id(), "local")));
        assertEquals("local", sessions.get(session.id()).location());
        assertEquals("idle", sessions.get(session.id()).status());
        assertTrue(types.contains("move-progress"), types.toString());
    }

    /** Subscribes, runs the action, and waits until an event matching the predicate arrives. */
    private void awaitEvent(String sessionId, List<String> types, Predicate<SessionEvent> until, Runnable action)
            throws InterruptedException {
        CountDownLatch arrived = new CountDownLatch(1);
        try (SessionEventStream.Subscription ignored = events.subscribe(sessionId, event -> {
            types.add(event.type());
            if (until.test(event)) {
                arrived.countDown();
            }
        })) {
            action.run();
            assertTrue(arrived.await(10, TimeUnit.SECONDS), "timed out waiting for the event; saw " + types);
        }
    }
}
