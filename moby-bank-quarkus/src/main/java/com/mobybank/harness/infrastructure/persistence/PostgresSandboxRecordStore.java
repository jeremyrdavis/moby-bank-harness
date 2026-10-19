package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.infrastructure.sbx.SandboxRecord;
import com.mobybank.harness.infrastructure.sbx.SandboxRecordStore;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;

/** Keeps the sandbox bookkeeping in PostgreSQL, so a restart does not have to rediscover it by listing sandboxes. */
@ApplicationScoped
public class PostgresSandboxRecordStore implements SandboxRecordStore {

    private final SandboxRecordJpaRepository jpa;

    @Inject
    public PostgresSandboxRecordStore(SandboxRecordJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Optional<SandboxRecord> find(SessionId session) {
        return Optional.ofNullable(jpa.findById(session.value())).map(row -> new SandboxRecord(
                row.sandboxName == null ? null : new Handle(row.location, row.sandboxName, row.generation),
                row.agentSessionId, row.awaitingFiles));
    }

    @Override
    @Transactional
    public void save(SessionId session, SandboxRecord record) {
        SandboxRecordEntity row = jpa.findById(session.value());
        boolean isNew = row == null;
        if (isNew) {
            row = new SandboxRecordEntity();
            row.sessionId = session.value();
        }
        Handle handle = record.handle();
        row.location = handle == null ? null : handle.location();
        row.sandboxName = handle == null ? null : handle.name();
        row.generation = handle == null ? null : handle.generation();
        row.agentSessionId = record.agentSession();
        row.awaitingFiles = record.awaitingFiles();
        if (isNew) {
            jpa.persist(row);
        }
    }
}
