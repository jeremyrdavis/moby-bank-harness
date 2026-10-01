package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Keeps each user's connected folders in memory, with the same copy-and-version rules as the session store. */
@ApplicationScoped
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
            throw new StaleAggregateException("connected folders", folders.userId().value(), folders.version(),
                    storedVersion);
        }
        stored.put(folders.userId(), copy(folders, folders.version() + 1));
    }

    private static ConnectedFolders copy(ConnectedFolders folders, long version) {
        return ConnectedFolders.rehydrate(folders.userId(), folders.folderIds(), version);
    }
}
