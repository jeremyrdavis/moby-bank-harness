package com.mobybank.harness.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectedFoldersTest {

    private static final FolderId F1 = new FolderId("f1");
    private static final FolderId F2 = new FolderId("f2");
    private static final FolderId F3 = new FolderId("f3");

    @Test
    void startsEmpty() {
        ConnectedFolders folders = ConnectedFolders.none(UserId.DEMO);
        assertTrue(folders.folderIds().isEmpty());
        assertEquals(UserId.DEMO, folders.userId());
    }

    @Test
    void connectKeepsOrderAndReportsWhatWasAdded() {
        ConnectedFolders folders = ConnectedFolders.none(UserId.DEMO);
        assertEquals(List.of(F1, F2), folders.connect(List.of(F1, F2)));
        assertEquals(List.of(F3), folders.connect(List.of(F2, F3)));
        assertEquals(List.of(F1, F2, F3), folders.folderIds());
    }

    @Test
    void connectingAnAlreadyConnectedFolderAddsNothing() {
        ConnectedFolders folders = ConnectedFolders.rehydrate(UserId.DEMO, List.of(F1), 0L);
        assertTrue(folders.connect(List.of(F1)).isEmpty());
        assertEquals(List.of(F1), folders.folderIds());
    }

    @Test
    void connectRequiresAtLeastOneFolder() {
        ConnectedFolders folders = ConnectedFolders.none(UserId.DEMO);
        assertThrows(IllegalArgumentException.class, () -> folders.connect(List.of()));
    }

    @Test
    void disconnectRemovesAFolder() {
        ConnectedFolders folders = ConnectedFolders.rehydrate(UserId.DEMO, List.of(F1, F2), 3L);
        folders.disconnect(F1);
        assertFalse(folders.isConnected(F1));
        assertTrue(folders.isConnected(F2));
        assertEquals(3L, folders.version());
    }

    @Test
    void folderIdsAreReturnedAsACopy() {
        ConnectedFolders folders = ConnectedFolders.rehydrate(UserId.DEMO, List.of(F1), 0L);
        assertThrows(UnsupportedOperationException.class, () -> folders.folderIds().add(F2));
    }
}
