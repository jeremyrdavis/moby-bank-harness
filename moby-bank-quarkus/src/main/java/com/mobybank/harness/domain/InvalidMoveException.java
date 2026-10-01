package com.mobybank.harness.domain;

/** The requested move is not allowed, for example moving a session to the location it is already in. */
public class InvalidMoveException extends DomainException {

    public InvalidMoveException(SessionId sessionId, Location from, Location to, String reason) {
        super("Cannot move session " + sessionId.value() + " from " + from + " to " + to + ": " + reason);
    }
}
