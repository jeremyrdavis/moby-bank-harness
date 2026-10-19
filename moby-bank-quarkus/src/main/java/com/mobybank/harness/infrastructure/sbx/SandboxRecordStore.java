package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.SessionId;
import java.util.Optional;

/**
 * Where {@link SandboxRegistry} keeps its {@link SandboxRecord}s. The application uses the PostgreSQL implementation,
 * so the bookkeeping survives a restart; tests use an in-memory one.
 */
public interface SandboxRecordStore {

    Optional<SandboxRecord> find(SessionId session);

    /** Replaces everything stored for the session. */
    void save(SessionId session, SandboxRecord record);
}
