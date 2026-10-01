package com.mobybank.harness.domain;

/** The stages of a move that a {@link SandboxTransfer} reports through {@link MoveProgress}. */
public enum MoveStage {
    /** Collecting the conversation and files into a transferable bundle. */
    PACKAGING,
    /** Sending the bundle to the target location. */
    TRANSFERRING
}
