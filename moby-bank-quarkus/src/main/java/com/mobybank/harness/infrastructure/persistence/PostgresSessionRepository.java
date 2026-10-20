package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.UserId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Stores {@link Session}s in PostgreSQL. It keeps the optimistic-version contract of {@link SessionRepository}: the row
 * is locked while it is compared and updated, so of two writers at the same version exactly one wins and the other
 * gets a {@link StaleAggregateException}. Each method is one short transaction.
 */
@ApplicationScoped
public class PostgresSessionRepository implements SessionRepository {

    private final SessionJpaRepository jpa;

    @Inject
    public PostgresSessionRepository(SessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Optional<Session> findById(SessionId id) {
        Optional<Session> found = Optional.ofNullable(jpa.findById(id.value())).map(SessionMapper::toAggregate);
        Log.tracef("findById %s: %s", id.value(), found.isPresent() ? "found" : "not found");
        return found;
    }

    @Override
    @Transactional
    public List<Session> findAllByMostRecentlyUpdated() {
        return jpa.find("select distinct s from SessionEntity s left join fetch s.messages order by s.updatedAt desc")
                .list().stream().map(SessionMapper::toAggregate).toList();
    }

    @Override
    @Transactional
    public List<Session> findAllByOwner(UserId owner) {
        // rows from before there were users have no owner and belong to the demo user
        String query = UserId.DEMO.equals(owner)
                ? "select distinct s from SessionEntity s left join fetch s.messages "
                        + "where s.ownerId = ?1 or s.ownerId is null order by s.updatedAt desc"
                : "select distinct s from SessionEntity s left join fetch s.messages "
                        + "where s.ownerId = ?1 order by s.updatedAt desc";
        return jpa.find(query, owner.value()).list().stream().map(SessionMapper::toAggregate).toList();
    }

    @Override
    @Transactional
    public void persist(Session session) {
        SessionEntity row = jpa.findById(session.id().value(), LockModeType.PESSIMISTIC_WRITE);
        long storedVersion = row == null ? 0L : row.version;
        if (storedVersion != session.version()) {
            Log.debugf("Rejected a stale write of session %s: holds version %d, the database has %d",
                    session.id().value(), session.version(), storedVersion);
            throw new StaleAggregateException("session", session.id().value().toString(), session.version(),
                    storedVersion);
        }
        boolean isNew = row == null;
        if (isNew) {
            row = new SessionEntity();
        }
        SessionMapper.copyInto(session, row);
        row.version = storedVersion + 1;
        try {
            if (isNew) {
                jpa.persist(row);
            }
            jpa.flush();
        } catch (PersistenceException e) {
            if (isNew && DatabaseErrors.isDuplicateKey(e)) {
                // Two writers inserted the same new session at once: the other one won, and its row is at version 1.
                Log.debugf("Insert of session %s lost a race: %s", session.id().value(), e.getMessage());
                throw new StaleAggregateException("session", session.id().value().toString(), session.version(), 1L);
            }
            throw e; // any other database failure is not a stale write: let it surface with its cause
        }
        Log.debugf("Persisted session %s: version %d -> %d, status %s, %d messages", session.id().value(),
                session.version(), row.version, session.status(), session.messages().size());
    }
}
