package com.mobybank.harness.application;

public record AttachUploadCommand(String sessionId, String fileName, byte[] content) {
}
