package com.mobybank.harness.domain;

/**
 * An aggregate was changed by someone else between loading it and saving it. The caller should reload and retry or
 * report a conflict.
 */
public class StaleAggregateException extends DomainException {

    public StaleAggregateException(String aggregate, String id, long loadedVersion, long storedVersion) {
        super(aggregate + " " + id + " was loaded at version " + loadedVersion + " but is now at version "
                + storedVersion);
    }
}
