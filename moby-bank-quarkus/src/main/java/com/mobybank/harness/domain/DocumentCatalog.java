package com.mobybank.harness.domain;

import java.util.List;

/** The document source analysts connect folders from (OneDrive in production). */
public interface DocumentCatalog {

    /** Every folder that can be connected. */
    List<Folder> folders();

    /** The documents inside one folder; empty if the folder is unknown. */
    List<CatalogFile> files(FolderId folderId);
}
