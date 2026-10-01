package com.mobybank.harness.domain;

import java.util.Objects;

/** A document folder in the catalog that an analyst can connect. */
public record Folder(FolderId id, String name, String path, int fileCount) {

    public Folder {
        Objects.requireNonNull(id, "folder id required");
        Objects.requireNonNull(name, "folder name required");
        Objects.requireNonNull(path, "folder path required");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("folder name must not be blank");
        }
        if (fileCount < 0) {
            throw new IllegalArgumentException("file count must not be negative");
        }
    }
}
