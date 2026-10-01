package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.SessionId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps uploaded files in memory, keyed by session and file name. State is lost on restart. */
@ApplicationScoped
public class InMemoryUploadStore implements UploadStore {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public void store(SessionId sessionId, String fileName, byte[] content) {
        files.put(key(sessionId, fileName), content.clone());
    }

    @Override
    public Optional<byte[]> find(SessionId sessionId, String fileName) {
        return Optional.ofNullable(files.get(key(sessionId, fileName))).map(byte[]::clone);
    }

    private static String key(SessionId sessionId, String fileName) {
        return sessionId.value() + "/" + fileName;
    }
}
