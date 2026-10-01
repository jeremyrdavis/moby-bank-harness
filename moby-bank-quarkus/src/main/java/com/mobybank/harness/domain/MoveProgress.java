package com.mobybank.harness.domain;

import java.util.Objects;

/** A progress report emitted by a transfer while it moves a session. */
public record MoveProgress(MoveStage stage, String detail) {

    public MoveProgress {
        Objects.requireNonNull(stage, "stage required");
        Objects.requireNonNull(detail, "detail required");
    }
}
