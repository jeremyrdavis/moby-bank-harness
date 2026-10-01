package com.mobybank.harness.application;

import java.util.List;

public record SendMessageCommand(String sessionId, String text, List<FileRefDTO> files) {
}
