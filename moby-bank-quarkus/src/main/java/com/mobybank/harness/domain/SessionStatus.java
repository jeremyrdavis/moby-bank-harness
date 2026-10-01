package com.mobybank.harness.domain;

/** What a {@link Session} is doing right now. Only an {@link #IDLE} session accepts a new message or a move. */
public enum SessionStatus {
    /** Ready for a new message or a move. */
    IDLE,
    /** The agent is working on a turn. */
    RUNNING,
    /** The session is being moved between locations. */
    MOVING
}
