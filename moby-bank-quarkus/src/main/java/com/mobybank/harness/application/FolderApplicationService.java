package com.mobybank.harness.application;

import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.UserId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Orchestrates the document-source use cases: browse the folder library, connect folders, list their files. */
@ApplicationScoped
public class FolderApplicationService {

    private static final UserId USER = UserId.DEMO;

    private final DocumentCatalog catalog;
    private final ConnectedFoldersRepository connectedFolders;

    @Inject
    public FolderApplicationService(DocumentCatalog catalog, ConnectedFoldersRepository connectedFolders) {
        this.catalog = catalog;
        this.connectedFolders = connectedFolders;
    }

    /** Every folder that can be connected, flagged with whether it already is. */
    public List<FolderDTO> library() {
        Set<FolderId> connected = Set.copyOf(current().folderIds());
        List<FolderDTO> library = catalog.folders().stream()
                .map(folder -> toDto(folder, connected.contains(folder.id())))
                .toList();
        Log.debugf("Folder library: %d folders, %d connected", library.size(), connected.size());
        return library;
    }

    /** Only the folders that are connected, in library order. */
    public List<FolderDTO> connected() {
        return library().stream().filter(FolderDTO::connected).toList();
    }

    /** Connects the chosen folders and returns the updated library. Already-connected folders are ignored. */
    public List<FolderDTO> connect(ConnectFoldersCommand command) {
        List<String> requested = command.folderIds() == null ? List.of() : command.folderIds();
        Set<FolderId> known = catalog.folders().stream().map(Folder::id).collect(Collectors.toSet());
        List<FolderId> ids = requested.stream().map(FolderId::new).toList();
        Log.debugf("Connecting %d folders (%d known in the catalog)", ids.size(), known.size());
        ids.stream().filter(id -> !known.contains(id)).findFirst().ifPresent(unknown -> {
            Log.debugf("Folder %s is not in the catalog", unknown.value());
            throw new ResourceNotFoundException("folder", unknown.value());
        });

        ConnectedFolders folders = current();
        folders.connect(ids);
        connectedFolders.persist(folders);
        Log.debugf("Connected folders are now: %d", folders.folderIds().size());
        return library();
    }

    /** The documents in every connected folder, for the attach dialog. */
    public List<CatalogFileDTO> connectedFiles() {
        Map<FolderId, Folder> byId = catalog.folders().stream()
                .collect(Collectors.toMap(Folder::id, Function.identity(), (a, b) -> a));
        Set<FolderId> connected = Set.copyOf(current().folderIds());
        List<CatalogFileDTO> files = catalog.folders().stream()
                .filter(folder -> connected.contains(folder.id()))
                .flatMap(folder -> catalog.files(folder.id()).stream())
                .map(file -> toDto(file, byId.get(file.folderId())))
                .toList();
        Log.debugf("Listed %d files across %d connected folders", files.size(), connected.size());
        return files;
    }

    private ConnectedFolders current() {
        return connectedFolders.findByUser(USER).orElseGet(() -> ConnectedFolders.none(USER));
    }

    private static FolderDTO toDto(Folder folder, boolean connected) {
        return new FolderDTO(folder.id().value(), folder.name(), folder.path(), folder.fileCount(), connected);
    }

    private static CatalogFileDTO toDto(CatalogFile file, Folder folder) {
        return new CatalogFileDTO(file.folderId().value(), folder.name(), folder.path(), file.name());
    }
}
