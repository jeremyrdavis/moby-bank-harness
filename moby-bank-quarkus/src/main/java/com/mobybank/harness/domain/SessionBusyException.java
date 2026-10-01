package com.mobybank.harness.domain;

/** The session is running a turn or moving, so it cannot accept the requested operation right now. */
public class SessionBusyException extends DomainException {

    private final SessionId sessionId;
    private final SessionStatus status;

    public SessionBusyException(SessionId sessionId, SessionStatus status) {
        super("Session " + sessionId.value() + " is " + status + " and cannot accept this operation");
        this.sessionId = sessionId;
        this.status = status;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public SessionStatus status() {
        return status;
    }
}
