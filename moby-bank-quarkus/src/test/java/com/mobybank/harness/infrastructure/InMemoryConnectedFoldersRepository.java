package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.UserId;
import io.quarkus.logging.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A test double for the PostgreSQL repository: keeps each user's connected folders in memory, with the same copy-and-version rules as the session store. */
public class InMemoryConnectedFoldersRepository implements ConnectedFoldersRepository {

    private final Map<UserId, ConnectedFolders> stored = new HashMap<>();

    @Override
    public synchronized Optional<ConnectedFolders> findByUser(UserId userId) {
        return Optional.ofNullable(stored.get(userId)).map(folders -> copy(folders, folders.version()));
    }

    @Override
    public synchronized void persist(ConnectedFolders folders) {
        ConnectedFolders existing = stored.get(folders.userId());
        long storedVersion = existing == null ? 0L : existing.version();
        if (storedVersion != folders.version()) {
            Log.debugf("Rejected a stale write of connected folders for %s: holds version %d, store has %d",
                    folders.userId().value(), folders.version(), storedVersion);
            throw new StaleAggregateException("connected folders", folders.userId().value(), folders.version(),
                    storedVersion);
        }
        stored.put(folders.userId(), copy(folders, folders.version() + 1));
        Log.debugf("Persisted connected folders for %s: version %d -> %d, %d folders", folders.userId().value(),
                folders.version(), folders.version() + 1, folders.folderIds().size());
    }

    private static ConnectedFolders copy(ConnectedFolders folders, long version) {
        return ConnectedFolders.rehydrate(folders.userId(), folders.folderIds(), version);
    }
}
