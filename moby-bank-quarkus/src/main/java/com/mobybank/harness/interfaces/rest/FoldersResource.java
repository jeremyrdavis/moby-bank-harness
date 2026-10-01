package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.application.CatalogFileDTO;
import com.mobybank.harness.application.ConnectFoldersCommand;
import com.mobybank.harness.application.FolderApplicationService;
import com.mobybank.harness.application.FolderDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Endpoints for the OneDrive folder library: browse it, connect folders, and list the documents of the connected ones. */
@Path("/api/folders")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Folders")
public class FoldersResource {

    private final FolderApplicationService folders;

    @Inject
    public FoldersResource(FolderApplicationService folders) {
        this.folders = folders;
    }

    @GET
    @Operation(summary = "The folder library",
            description = "Every folder that can be connected, each flagged with whether it already is. "
                    + "Filter on `connected` for the sidebar list.")
    public List<FolderDTO> library() {
        return folders.library();
    }

    @POST
    @Path("/connect")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Connect folders",
            description = "Connects the chosen folders; ones already connected are ignored. Returns the updated library.")
    @APIResponse(responseCode = "400", description = "No folders chosen",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @APIResponse(responseCode = "404", description = "One of the folders does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<FolderDTO> connect(ConnectFoldersRequest request) {
        return folders.connect(new ConnectFoldersCommand(request == null ? null : request.folderIds()));
    }

    @GET
    @Path("/connected/files")
    @Operation(summary = "Documents in the connected folders",
            description = "What the attach dialog offers: each document with its folder's name and path.")
    public List<CatalogFileDTO> connectedFiles() {
        return folders.connectedFiles();
    }
}
