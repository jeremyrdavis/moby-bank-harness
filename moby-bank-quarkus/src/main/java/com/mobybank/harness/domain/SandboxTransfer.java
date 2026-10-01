package com.mobybank.harness.domain;

import java.util.function.Consumer;

/** Carries a session's context and files from one location to the other. */
public interface SandboxTransfer {

    /**
     * Moves the session and returns when the target location is ready to run it.
     *
     * @param onProgress called as the transfer advances through its stages
     * @throws SandboxFailureException if the move could not be completed
     */
    void move(MoveRequest request, Consumer<MoveProgress> onProgress);
}
