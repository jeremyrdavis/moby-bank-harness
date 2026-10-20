package com.mobybank.harness.application;

import com.mobybank.harness.domain.UserId;
import java.time.Duration;
import java.util.Optional;

/** Keeps the sessions of signed-in users, found by the secret token the browser holds. */
public interface LoginSessionStore {

    /** Starts a session for the user and returns its token, the only copy of it that exists in readable form. */
    String open(UserId user, Duration timeToLive);

    /** The user a token belongs to, unless it is unknown or has expired. */
    Optional<UserId> resolve(String token);

    /** Ends the session. Unknown tokens are ignored. */
    void close(String token);
}
