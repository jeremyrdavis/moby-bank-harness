package com.mobybank.harness.domain;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Optional<Session> findById(SessionId id);

    /** All sessions, most recently updated first. */
    List<Session> findAllByMostRecentlyUpdated();

    void persist(Session session);
}
