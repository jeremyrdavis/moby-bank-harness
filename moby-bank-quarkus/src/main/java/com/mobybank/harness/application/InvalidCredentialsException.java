package com.mobybank.harness.application;

/** The email or password was wrong, or too many attempts were made. The message is the same for unknown users. */
public class InvalidCredentialsException extends RuntimeException {

    private final boolean lockedOut;

    public InvalidCredentialsException(String message) {
        this(message, false);
    }

    public InvalidCredentialsException(String message, boolean lockedOut) {
        super(message);
        this.lockedOut = lockedOut;
    }

    /** True when the sign-in was refused because of too many failed attempts, not because of a wrong password. */
    public boolean lockedOut() {
        return lockedOut;
    }
}
