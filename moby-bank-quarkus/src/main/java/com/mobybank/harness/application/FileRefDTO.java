package com.mobybank.harness.application;

/** A file attached to a message. {@code source} is {@code upload} or {@code onedrive}. */
public record FileRefDTO(String name, String source) {
}
