package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.SessionId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;

/**
 * Keeps uploaded files in PostgreSQL, keyed by session and file name. Storing a name again replaces the content, and
 * does so atomically: the write is one {@code INSERT ... ON CONFLICT DO UPDATE}, so two uploads of the same new name
 * at once both succeed (the later one wins) instead of one failing on the duplicate key.
 */
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
        jpa.getEntityManager().createNativeQuery("""
                insert into upload (id, session_id, file_name, content) values (:id, :sessionId, :fileName, :content)
                on conflict (id) do update set content = excluded.content""")
                .setParameter("id", key(sessionId, fileName))
                .setParameter("sessionId", sessionId.value())
                .setParameter("fileName", fileName)
                .setParameter("content", content.clone())
                .executeUpdate();
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
