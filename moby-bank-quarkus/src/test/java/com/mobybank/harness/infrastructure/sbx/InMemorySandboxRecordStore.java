package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.SessionId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A test double for the PostgreSQL store: keeps the records in memory. */
public class InMemorySandboxRecordStore implements SandboxRecordStore {

    private final Map<SessionId, SandboxRecord> records = new HashMap<>();

    @Override
    public synchronized Optional<SandboxRecord> find(SessionId session) {
        return Optional.ofNullable(records.get(session));
    }

    @Override
    public synchronized void save(SessionId session, SandboxRecord record) {
        records.put(session, record);
    }
}
