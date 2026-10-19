package com.mobybank.harness.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class DatabaseErrorsTest {

    @Test
    void aDuplicateKeyViolationAnywhereInTheCauseChainIsRecognised() {
        SQLException duplicate = new SQLException("duplicate key value", "23505");

        assertTrue(DatabaseErrors.isDuplicateKey(duplicate));
        assertTrue(DatabaseErrors.isDuplicateKey(new PersistenceException(new RuntimeException(duplicate))));
    }

    @Test
    void otherDatabaseFailuresAreNotDuplicateKeys() {
        assertFalse(DatabaseErrors.isDuplicateKey(new SQLException("connection refused", "08001")));
        assertFalse(DatabaseErrors.isDuplicateKey(new SQLException("not null violation", "23502")));
        assertFalse(DatabaseErrors.isDuplicateKey(new PersistenceException("something else")));
        assertFalse(DatabaseErrors.isDuplicateKey(null));
    }
}
