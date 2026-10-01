package com.mobybank.harness.domain;

/** The document source (OneDrive) could not be reached or refused the request. */
public class DocumentSourceException extends DomainException {

    public DocumentSourceException(String message) {
        super(message);
    }

    public DocumentSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
