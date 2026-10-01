package com.mobybank.harness.application;

import java.util.List;

/** Asks to send a message in a session. {@code text} may be empty when {@code files} are attached. The message is stored at once and the agent starts on it in the background. */
public record SendMessageCommand(String sessionId, String text, List<FileRefDTO> files) {
}
