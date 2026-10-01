package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.SessionEvent;
import com.mobybank.harness.application.SessionEventStream;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

/**
 * Delivers session events to the listeners subscribed to that session, on the publishing thread. A listener that
 * throws never stops the others or the publisher. Nothing is buffered: a listener only sees events published after
 * it subscribed.
 */
@ApplicationScoped
public class InMemorySessionEventStream implements SessionEventStream {

    private static final Logger LOG = Logger.getLogger(InMemorySessionEventStream.class);

    private final Map<String, List<Consumer<SessionEvent>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(SessionEvent event) {
        List<Consumer<SessionEvent>> forSession = listeners.get(event.sessionId());
        if (forSession == null) {
            return;
        }
        for (Consumer<SessionEvent> listener : forSession) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOG.warnf(e, "A listener failed on %s for session %s", event.type(), event.sessionId());
            }
        }
    }

    @Override
    public Subscription subscribe(String sessionId, Consumer<SessionEvent> listener) {
        listeners.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.computeIfPresent(sessionId, (id, current) -> {
            current.remove(listener);
            return current.isEmpty() ? null : current;
        });
    }
}
