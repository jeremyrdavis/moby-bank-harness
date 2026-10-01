package com.mobybank.harness.domain;

import java.util.List;
import java.util.Objects;

/** Everything a transfer needs to carry a session from one location to the other. */
public record MoveRequest(SessionId sessionId, Location from, Location to, List<Message> history, List<FileRef> files) {

    public MoveRequest {
        Objects.requireNonNull(sessionId, "session id required");
        Objects.requireNonNull(from, "source location required");
        Objects.requireNonNull(to, "target location required");
        Objects.requireNonNull(history, "history required");
        Objects.requireNonNull(files, "files required");
        if (from == to) {
            throw new IllegalArgumentException("source and target location must differ");
        }
        history = List.copyOf(history);
        files = List.copyOf(files);
    }
}
