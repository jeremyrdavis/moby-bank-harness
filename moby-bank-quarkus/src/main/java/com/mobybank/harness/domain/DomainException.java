package com.mobybank.harness.domain;

/** Base type for rule violations and port failures raised by the domain. The REST layer maps these to HTTP errors. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
