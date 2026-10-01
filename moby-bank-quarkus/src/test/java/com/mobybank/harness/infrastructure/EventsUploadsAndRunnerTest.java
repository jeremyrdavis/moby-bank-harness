package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.SessionEvent;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEvent.Thinking;
import com.mobybank.harness.application.SessionEventStream.Subscription;
import com.mobybank.harness.domain.SessionId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EventsUploadsAndRunnerTest {

    // --- event stream ------------------------------------------------------------------------------------------

    @Test
    void listenersOnlyHearTheirOwnSession() {
        InMemorySessionEventStream stream = new InMemorySessionEventStream();
        List<SessionEvent> forA = new CopyOnWriteArrayList<>();
        List<SessionEvent> forB = new CopyOnWriteArrayList<>();
        stream.subscribe("a", forA::add);
        stream.subscribe("b", forB::add);

        stream.publish(new Thinking("a", "Working…"));
        stream.publish(new MoveCompleted("b", "cloud"));

        assertEquals(1, forA.size());
        assertEquals("thinking", forA.get(0).type());
        assertEquals(1, forB.size());
        assertEquals("moved", forB.get(0).type());
    }

    @Test
    void severalListenersAllReceiveAnEvent() {
        InMemorySessionEventStream stream = new InMemorySessionEventStream();
        List<String> seen = new CopyOnWriteArrayList<>();
        stream.subscribe("a", e -> seen.add("one"));
        stream.subscribe("a", e -> seen.add("two"));
        stream.publish(new Thinking("a", "x"));
        assertEquals(List.of("one", "two"), seen);
    }

    @Test
    void closingASubscriptionStopsDelivery() {
        InMemorySessionEventStream stream = new InMemorySessionEventStream();
        List<SessionEvent> seen = new CopyOnWriteArrayList<>();
        Subscription subscription = stream.subscribe("a", seen::add);

        stream.publish(new Thinking("a", "first"));
        subscription.close();
        stream.publish(new Thinking("a", "second"));
        subscription.close(); // closing twice is harmless

        assertEquals(1, seen.size());
    }

    @Test
    void aFailingListenerDoesNotStopTheOthersOrThePublisher() {
        InMemorySessionEventStream stream = new InMemorySessionEventStream();
        List<String> seen = new CopyOnWriteArrayList<>();
        stream.subscribe("a", e -> {
            throw new IllegalStateException("broken client");
        });
        stream.subscribe("a", e -> seen.add("ok"));

        stream.publish(new Thinking("a", "x"));

        assertEquals(List.of("ok"), seen);
    }

    @Test
    void publishingWithNoListenersIsFine() {
        new InMemorySessionEventStream().publish(new Thinking("nobody", "x"));
    }

    // --- uploads -----------------------------------------------------------------------------------------------

    @Test
    void uploadsAreStoredPerSessionAndFileName() {
        InMemoryUploadStore store = new InMemoryUploadStore();
        SessionId one = SessionId.fresh();
        SessionId two = SessionId.fresh();

        store.store(one, "notes.docx", new byte[] {1, 2, 3});

        assertArrayEquals(new byte[] {1, 2, 3}, store.find(one, "notes.docx").orElseThrow());
        assertTrue(store.find(two, "notes.docx").isEmpty());
        assertTrue(store.find(one, "other.docx").isEmpty());
    }

    @Test
    void uploadsAreCopiedSoCallersCannotChangeStoredBytes() {
        InMemoryUploadStore store = new InMemoryUploadStore();
        SessionId id = SessionId.fresh();
        byte[] content = {1, 2, 3};
        store.store(id, "a.txt", content);

        content[0] = 9;
        store.find(id, "a.txt").orElseThrow()[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, store.find(id, "a.txt").orElseThrow());
    }

    // --- background runner -------------------------------------------------------------------------------------

    @Test
    void tasksRunOffTheCallersThread() throws InterruptedException {
        VirtualThreadBackgroundRunner runner = new VirtualThreadBackgroundRunner();
        AtomicReference<Thread> ranOn = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        runner.run(() -> {
            ranOn.set(Thread.currentThread());
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread(), ranOn.get());
        assertTrue(ranOn.get().isVirtual());
        runner.shutdown();
    }

    @Test
    void aFailingTaskDoesNotStopLaterTasks() throws InterruptedException {
        VirtualThreadBackgroundRunner runner = new VirtualThreadBackgroundRunner();
        CountDownLatch done = new CountDownLatch(1);

        runner.run(() -> {
            throw new IllegalStateException("task blew up");
        });
        runner.run(done::countDown);

        assertTrue(done.await(5, TimeUnit.SECONDS));
        runner.shutdown();
    }
}
