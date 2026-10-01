package com.mobybank.harness.domain;

import java.util.Objects;

/** A file attached to a message, identified by its name and where it came from. */
public record FileRef(String name, FileSource source) {

    public FileRef {
        Objects.requireNonNull(name, "file name required");
        Objects.requireNonNull(source, "file source required");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
    }
}
