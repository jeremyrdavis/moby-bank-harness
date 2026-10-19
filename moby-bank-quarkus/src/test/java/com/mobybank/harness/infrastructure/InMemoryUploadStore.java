package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.SessionId;
import io.quarkus.logging.Log;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A test double for the PostgreSQL upload store: keeps uploaded files in memory, keyed by session and file name. State is lost on restart. */
public class InMemoryUploadStore implements UploadStore {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public void store(SessionId sessionId, String fileName, byte[] content) {
        files.put(key(sessionId, fileName), content.clone());
        Log.debugf("Stored an upload for session %s: %d bytes", sessionId.value(), content.length);
    }

    @Override
    public Optional<byte[]> find(SessionId sessionId, String fileName) {
        Optional<byte[]> found = Optional.ofNullable(files.get(key(sessionId, fileName))).map(byte[]::clone);
        Log.debugf("Upload lookup for session %s: %s", sessionId.value(), found.isPresent() ? "found" : "missing");
        return found;
    }

    private static String key(SessionId sessionId, String fileName) {
        return sessionId.value() + "/" + fileName;
    }
}
