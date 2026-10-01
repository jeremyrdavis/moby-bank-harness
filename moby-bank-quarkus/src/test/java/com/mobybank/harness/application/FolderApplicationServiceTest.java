package com.mobybank.harness.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.Fakes.Catalog;
import com.mobybank.harness.application.Fakes.FoldersRepo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FolderApplicationServiceTest {

    private FoldersRepo repo;
    private FolderApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new FoldersRepo();
        service = new FolderApplicationService(new Catalog(), repo);
    }

    @Test
    void theLibraryListsEveryFolderAndNoneAreConnectedAtFirst() {
        List<FolderDTO> library = service.library();
        assertEquals(3, library.size());
        assertTrue(library.stream().noneMatch(FolderDTO::connected));
        assertEquals("Earnings 2026", library.get(0).name());
        assertEquals(24, library.get(0).fileCount());
        assertTrue(service.connected().isEmpty());
    }

    @Test
    void connectingFlagsFoldersInTheLibraryAndListsThemAsConnected() {
        List<FolderDTO> library = service.connect(new ConnectFoldersCommand(List.of("f3", "f1")));

        assertEquals(List.of(true, false, true), library.stream().map(FolderDTO::connected).toList());
        assertEquals(List.of("f1", "f3"), service.connected().stream().map(FolderDTO::id).toList());
    }

    @Test
    void connectingAgainKeepsWhatWasAlreadyConnected() {
        service.connect(new ConnectFoldersCommand(List.of("f1")));
        service.connect(new ConnectFoldersCommand(List.of("f1", "f2")));
        assertEquals(List.of("f1", "f2"), service.connected().stream().map(FolderDTO::id).toList());
    }

    @Test
    void anUnknownFolderIsNotFoundAndNothingIsSaved() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.connect(new ConnectFoldersCommand(List.of("f1", "nope"))));
        assertEquals(0, repo.persistCount);
        assertFalse(service.library().get(0).connected());
    }

    @Test
    void connectingNothingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.connect(new ConnectFoldersCommand(List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.connect(new ConnectFoldersCommand(null)));
    }

    @Test
    void aBlankFolderIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.connect(new ConnectFoldersCommand(List.of(" "))));
    }

    @Test
    void connectedFilesComeFromConnectedFoldersOnlyWithTheirFolderDetails() {
        assertTrue(service.connectedFiles().isEmpty());

        service.connect(new ConnectFoldersCommand(List.of("f1", "f2")));
        List<CatalogFileDTO> files = service.connectedFiles();

        assertEquals(2, files.size(), "f2 has no files and f3 is not connected");
        assertEquals("Fathom_Q2_2026_10-Q.pdf", files.get(0).name());
        assertEquals("Earnings 2026", files.get(0).folderName());
        assertEquals("Credit Research / Coverage / Earnings 2026", files.get(0).folderPath());
        assertEquals("f1", files.get(0).folderId());
    }
}
