package com.mobybank.harness.domain;

import java.util.List;
import java.util.Optional;

/** The document source analysts connect folders from (OneDrive in production). */
public interface DocumentCatalog {

    /** Every folder that can be connected. */
    List<Folder> folders();

    /** The documents inside one folder; empty if the folder is unknown. */
    List<CatalogFile> files(FolderId folderId);

    /**
     * The content of a document, found by its file name. Names are assumed unique across the catalog; if two
     * folders hold the same name, the first folder's document is returned.
     *
     * @throws SandboxFailureException if the catalog cannot be reached
     */
    Optional<byte[]> fetch(String fileName);
}
