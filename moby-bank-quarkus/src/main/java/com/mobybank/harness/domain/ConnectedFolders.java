package com.mobybank.harness.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The document folders one analyst has connected, in the order they were connected. Folders are referenced by id
 * only; their details come from the {@link DocumentCatalog}.
 */
public class ConnectedFolders {

    private final UserId userId;
    private long version;
    private final Set<FolderId> folderIds;

    private ConnectedFolders(UserId userId, Set<FolderId> folderIds, long version) {
        this.userId = userId;
        this.folderIds = new LinkedHashSet<>(folderIds);
        this.version = version;
    }

    /** Starts with no folders connected. */
    public static ConnectedFolders none(UserId userId) {
        Objects.requireNonNull(userId, "user id required");
        return new ConnectedFolders(userId, Set.of(), 0L);
    }

    /** Reconstruction path for the persistence layer. Domain code never calls it. */
    public static ConnectedFolders rehydrate(UserId userId, List<FolderId> folderIds, long version) {
        return new ConnectedFolders(userId, new LinkedHashSet<>(folderIds), version);
    }

    /**
     * Connects the given folders. Folders that are already connected are ignored.
     *
     * @return the folders that were newly connected, in request order
     */
    public List<FolderId> connect(Collection<FolderId> requested) {
        Objects.requireNonNull(requested, "folders required");
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("choose at least one folder to connect");
        }
        List<FolderId> added = new ArrayList<>();
        for (FolderId id : requested) {
            if (folderIds.add(Objects.requireNonNull(id, "folder id required"))) {
                added.add(id);
            }
        }
        return List.copyOf(added);
    }

    public void disconnect(FolderId folderId) {
        Objects.requireNonNull(folderId, "folder id required");
        folderIds.remove(folderId);
    }

    public boolean isConnected(FolderId folderId) {
        return folderIds.contains(folderId);
    }

    public UserId userId() {
        return userId;
    }

    public List<FolderId> folderIds() {
        return List.copyOf(folderIds);
    }

    public long version() {
        return version;
    }
}
