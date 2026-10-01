package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.StaleAggregateException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps sessions in memory. It stores a private copy and hands out fresh copies, so callers never share mutable
 * aggregates, and it enforces the optimistic-version contract of {@link SessionRepository#persist(Session)}. State
 * is lost on restart.
 */
@ApplicationScoped
public class InMemorySessionRepository implements SessionRepository {

    private final Map<SessionId, Session> stored = new HashMap<>();

    @Override
    public synchronized Optional<Session> findById(SessionId id) {
        return Optional.ofNullable(stored.get(id)).map(session -> copy(session, session.version()));
    }

    @Override
    public synchronized List<Session> findAllByMostRecentlyUpdated() {
        return stored.values().stream()
                .map(session -> copy(session, session.version()))
                .sorted(Comparator.comparing(Session::updatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized void persist(Session session) {
        Session existing = stored.get(session.id());
        long storedVersion = existing == null ? 0L : existing.version();
        if (storedVersion != session.version()) {
            throw new StaleAggregateException("session", session.id().value().toString(), session.version(),
                    storedVersion);
        }
        stored.put(session.id(), copy(session, session.version() + 1));
    }

    private static Session copy(Session session, long version) {
        return Session.rehydrate(session.id(), session.title(), session.location(), session.status(),
                session.moveTarget(), session.messages(), session.createdAt(), session.updatedAt(), version);
    }
}
