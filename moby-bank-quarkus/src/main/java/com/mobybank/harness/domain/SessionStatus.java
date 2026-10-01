package com.mobybank.harness.domain;

public enum SessionStatus {
    /** Ready for a new message or a move. */
    IDLE,
    /** The agent is working on a turn. */
    RUNNING,
    /** The session is being moved between locations. */
    MOVING
}
