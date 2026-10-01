package com.mobybank.harness.domain;

import java.util.Objects;

/** Opaque identifier of a document folder in the catalog (an item id when the catalog is OneDrive). */
public record FolderId(String value) {

    public FolderId {
        Objects.requireNonNull(value, "folder id required");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("folder id must not be blank");
        }
    }
}
