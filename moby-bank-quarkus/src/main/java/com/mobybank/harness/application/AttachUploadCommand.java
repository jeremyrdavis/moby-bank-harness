package com.mobybank.harness.application;

/** Asks to store a file the analyst uploaded, so it can be attached to a later message. The file name is reduced to its base name, so a path can never escape the session's upload area. */
public record AttachUploadCommand(String sessionId, String fileName, byte[] content) {
}
