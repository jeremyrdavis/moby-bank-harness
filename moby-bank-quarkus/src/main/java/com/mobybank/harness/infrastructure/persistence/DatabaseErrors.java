package com.mobybank.harness.infrastructure.persistence;

import java.sql.SQLException;

/** Tells apart the database failures the repositories treat specially. */
final class DatabaseErrors {

    /** PostgreSQL's SQLSTATE for a unique or primary-key violation. */
    private static final String UNIQUE_VIOLATION = "23505";

    private DatabaseErrors() {
    }

    /** True when the failure, or anything that caused it, is a duplicate-key violation. */
    static boolean isDuplicateKey(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
