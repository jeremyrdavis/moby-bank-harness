package com.mobybank.harness.domain;

import java.util.List;
import java.util.Optional;

/** The document source analysts connect folders from (OneDrive in production). */
public interface DocumentCatalog {

    /**
     * Every folder that can be connected.
     *
     * @throws DocumentSourceException if the source cannot be reached
     */
    List<Folder> folders();

    /**
     * The documents inside one folder; empty if the folder is unknown.
     *
     * @throws DocumentSourceException if the source cannot be reached
     */
    List<CatalogFile> files(FolderId folderId);

    /**
     * The content of a document, found by its file name. Names are assumed unique across the catalog; if two
     * folders hold the same name, the first folder's document is returned.
     *
     * @throws DocumentSourceException if the source cannot be reached or the file is too large to use
     */
    Optional<byte[]> fetch(String fileName);
}
