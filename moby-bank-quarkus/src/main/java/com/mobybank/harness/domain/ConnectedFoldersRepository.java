package com.mobybank.harness.domain;

import java.util.Optional;

public interface ConnectedFoldersRepository {

    Optional<ConnectedFolders> findByUser(UserId userId);

    /**
     * Saves the connected folders under the same optimistic-version contract as
     * {@link SessionRepository#persist(Session)}.
     *
     * @throws StaleAggregateException if the stored version differs from the aggregate's version
     */
    void persist(ConnectedFolders connectedFolders);
}
