package com.mobybank.harness.domain;

import java.util.List;
import java.util.Optional;

/** Stores {@link Session}s. The in-memory implementation lives in the infrastructure layer; a database-backed one could replace it without touching the domain. */
public interface SessionRepository {

    Optional<Session> findById(SessionId id);

    /** All sessions of every user, most recently updated first. For housekeeping; use cases list by owner. */
    List<Session> findAllByMostRecentlyUpdated();

    /** The sessions that belong to one user, most recently updated first. */
    List<Session> findAllByOwner(UserId owner);

    /**
     * Saves the session. The stored version must equal {@link Session#version()}, the version it was loaded at;
     * each successful save advances the stored version by one, so reload before changing the session again.
     *
     * @throws StaleAggregateException if the stored version differs from the session's version
     */
    void persist(Session session);
}
