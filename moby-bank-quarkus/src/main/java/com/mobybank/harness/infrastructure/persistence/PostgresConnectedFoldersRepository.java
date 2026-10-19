package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.UserId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stores each user's {@link ConnectedFolders} in PostgreSQL, under the same version contract as the sessions. */
@ApplicationScoped
public class PostgresConnectedFoldersRepository implements ConnectedFoldersRepository {

    private final ConnectedFoldersJpaRepository jpa;

    @Inject
    public PostgresConnectedFoldersRepository(ConnectedFoldersJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Optional<ConnectedFolders> findByUser(UserId userId) {
        return Optional.ofNullable(jpa.findById(userId.value())).map(row -> ConnectedFolders.rehydrate(
                new UserId(row.userId), row.folderIds.stream().map(FolderId::new).toList(), row.version));
    }

    @Override
    @Transactional
    public void persist(ConnectedFolders folders) {
        ConnectedFoldersEntity row = jpa.findById(folders.userId().value(), LockModeType.PESSIMISTIC_WRITE);
        long storedVersion = row == null ? 0L : row.version;
        if (storedVersion != folders.version()) {
            Log.debugf("Rejected a stale write of connected folders for %s: holds version %d, the database has %d",
                    folders.userId().value(), folders.version(), storedVersion);
            throw new StaleAggregateException("connected folders", folders.userId().value(), folders.version(),
                    storedVersion);
        }
        boolean isNew = row == null;
        if (isNew) {
            row = new ConnectedFoldersEntity();
            row.userId = folders.userId().value();
        }
        row.folderIds = new ArrayList<>(folders.folderIds().stream().map(FolderId::value).toList());
        row.version = storedVersion + 1;
        try {
            if (isNew) {
                jpa.persist(row);
            }
            jpa.flush();
        } catch (PersistenceException e) {
            throw new StaleAggregateException("connected folders", folders.userId().value(), folders.version(), 1L);
        }
        Log.debugf("Persisted connected folders for %s: version %d -> %d, %d folders", folders.userId().value(),
                folders.version(), row.version, row.folderIds.size());
    }
}
