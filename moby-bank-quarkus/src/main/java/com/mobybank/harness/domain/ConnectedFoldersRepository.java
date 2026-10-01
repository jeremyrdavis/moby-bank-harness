package com.mobybank.harness.domain;

import java.util.Optional;

public interface ConnectedFoldersRepository {

    Optional<ConnectedFolders> findByUser(UserId userId);

    void persist(ConnectedFolders connectedFolders);
}
