package com.mobybank.harness.domain;

public enum MoveStage {
    /** Collecting the conversation and files into a transferable bundle. */
    PACKAGING,
    /** Sending the bundle to the target location. */
    TRANSFERRING
}
