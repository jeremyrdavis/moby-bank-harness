package com.mobybank.harness.application;

import com.mobybank.harness.domain.SessionId;
import java.util.Optional;

/** Holds files an analyst uploaded from their machine until a sandbox agent needs them. */
public interface UploadStore {

    void store(SessionId sessionId, String fileName, byte[] content);

    Optional<byte[]> find(SessionId sessionId, String fileName);
}
