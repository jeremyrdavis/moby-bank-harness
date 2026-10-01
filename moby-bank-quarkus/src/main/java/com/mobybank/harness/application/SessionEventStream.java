package com.mobybank.harness.application;

import java.util.function.Consumer;

/** Fans session events out to whoever is listening, such as open browser streams. */
public interface SessionEventStream {

    void publish(SessionEvent event);

    /** Listens to one session's events until the returned subscription is closed. */
    Subscription subscribe(String sessionId, Consumer<SessionEvent> listener);

    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
