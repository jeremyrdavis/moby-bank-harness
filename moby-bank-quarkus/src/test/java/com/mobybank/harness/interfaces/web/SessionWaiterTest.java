package com.mobybank.harness.interfaces.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.CreateSessionCommand;
import com.mobybank.harness.application.MessageDTO;
import com.mobybank.harness.application.MoveSessionCommand;
import com.mobybank.harness.application.SendMessageCommand;
import com.mobybank.harness.application.SessionApplicationService;
import com.mobybank.harness.application.SessionEvent.MessageAdded;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEventStream;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A request must take the outcome of its own work. The application persists "idle" before it publishes the outcome,
 * so the previous operation's outcome can still be in flight when the next request subscribes; these tests publish
 * such a late event by hand.
 */
@QuarkusTest
class SessionWaiterTest {

    @Inject
    SessionApplicationService sessions;
    @Inject
    SessionEventStream events;
    @Inject
    SessionWaiter waiter;

    @Test
    void aLateReplyFromThePreviousTurnIsNotTakenForTheNewOne() {
        String id = sessions.create(new CreateSessionCommand(null)).id();
        MessageDTO first = waiter.awaitReply(id, () -> send(id, "One")).orElseThrow();

        MessageDTO second = waiter.awaitReply(id, () -> {
            events.publish(new MessageAdded(id, first)); // the previous turn's event, arriving late
            send(id, "Two");
        }).orElseThrow();

        assertNotEquals(first.id(), second.id());
        assertEquals("assistant", second.role());
    }

    @Test
    void aLateMoveCompletionForTheOtherLocationIsNotTakenForThisMove() {
        String id = sessions.create(new CreateSessionCommand(null)).id();

        SessionWaiter.MoveOutcome outcome = waiter.awaitMove(id, "cloud", () -> {
            events.publish(new MoveCompleted(id, "local")); // a previous move back to local, arriving late
            sessions.move(new MoveSessionCommand(id, "cloud"));
        }).orElseThrow();

        assertTrue(outcome.completed());
        assertEquals("cloud", outcome.detail());
    }

    private void send(String id, String text) {
        sessions.sendMessage(new SendMessageCommand(id, text, List.of()));
    }
}
