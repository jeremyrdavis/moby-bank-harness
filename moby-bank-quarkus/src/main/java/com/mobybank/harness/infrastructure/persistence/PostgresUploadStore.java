package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.SessionId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;

/** Keeps uploaded files in PostgreSQL, keyed by session and file name. Storing a name again replaces the content. */
@ApplicationScoped
public class PostgresUploadStore implements UploadStore {

    private final UploadJpaRepository jpa;

    @Inject
    public PostgresUploadStore(UploadJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void store(SessionId sessionId, String fileName, byte[] content) {
        String key = key(sessionId, fileName);
        UploadEntity row = jpa.findById(key);
        if (row == null) {
            row = new UploadEntity();
            row.id = key;
            row.sessionId = sessionId.value();
            row.fileName = fileName;
            row.content = content.clone();
            jpa.persist(row);
        } else {
            row.content = content.clone();
        }
        Log.debugf("Stored an upload for session %s: %d bytes", sessionId.value(), content.length);
    }

    @Override
    @Transactional
    public Optional<byte[]> find(SessionId sessionId, String fileName) {
        Optional<byte[]> found = Optional.ofNullable(jpa.findById(key(sessionId, fileName))).map(row -> row.content.clone());
        Log.debugf("Upload lookup for session %s: %s", sessionId.value(), found.isPresent() ? "found" : "missing");
        return found;
    }

    private static String key(SessionId sessionId, String fileName) {
        return sessionId.value() + "/" + fileName;
    }
}
