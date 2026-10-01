package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.application.FileRefDTO;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record SendMessageRequest(
        @Schema(description = "The analyst's message; may be empty when files are attached") String text,
        @Schema(description = "Files to attach: uploaded earlier, or chosen from OneDrive") List<FileRefDTO> files) {
}
