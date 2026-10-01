package com.mobybank.harness.domain;

/** A sandbox port (agent or transfer) could not complete its work. Adapters wrap their own errors in this type. */
public class SandboxFailureException extends DomainException {

    public SandboxFailureException(String message) {
        super(message);
    }

    public SandboxFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
