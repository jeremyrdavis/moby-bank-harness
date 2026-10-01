package com.mobybank.harness.interfaces.rest;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for connecting folders. */
public record ConnectFoldersRequest(
        @Schema(description = "Ids of the folders to connect", required = true) List<String> folderIds) {
}
