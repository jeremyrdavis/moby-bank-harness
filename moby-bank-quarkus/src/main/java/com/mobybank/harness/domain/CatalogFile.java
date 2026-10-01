package com.mobybank.harness.domain;

import java.util.Objects;

/** A document inside a catalog folder. */
public record CatalogFile(FolderId folderId, String name) {

    public CatalogFile {
        Objects.requireNonNull(folderId, "folder id required");
        Objects.requireNonNull(name, "file name required");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
    }
}
