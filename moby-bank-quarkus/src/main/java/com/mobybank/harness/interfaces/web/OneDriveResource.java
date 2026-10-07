package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.CatalogFileDTO;
import com.mobybank.harness.application.ConnectFoldersCommand;
import com.mobybank.harness.application.FileRefDTO;
import com.mobybank.harness.application.FolderApplicationService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.RestForm;

/** The OneDrive fragments for {@code index.html}: connected folders in the sidebar and the connect and attach dialogs. */
@Path("/ui/onedrive")
@Produces(MediaType.TEXT_HTML)
@Blocking
public class OneDriveResource {

    private final FolderApplicationService folders;

    @Inject
    public OneDriveResource(FolderApplicationService folders) {
        this.folders = folders;
    }

    /** The folders the analyst has connected. */
    @GET
    @Path("/folders")
    public String connected() {
        return Templates.folders(folders.connected()).render();
    }

    /** A dialog: {@code mode=connect} lists every folder, {@code mode=attach} lists files in the connected ones. */
    @GET
    @Path("/picker")
    public String picker(@QueryParam("mode") String mode) {
        return switch (mode == null ? "" : mode) {
            case "connect" -> Templates.connectPicker(folders.library()).render();
            case "attach" -> Templates.attachPicker(folders.connectedFiles()).render();
            default -> throw new IllegalArgumentException("mode must be connect or attach");
        };
    }

    /** Connects the chosen folders, then closes the dialog and refreshes the sidebar. */
    @POST
    @Path("/connect")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response connect(@RestForm("folder") List<String> chosen) {
        List<String> ids = chosen == null ? List.of() : chosen;
        Hx.Triggers triggers = Hx.triggers().event(Hx.CLOSE_DIALOG);
        if (!ids.isEmpty()) {
            folders.connect(new ConnectFoldersCommand(ids));
            triggers.event(Hx.FOLDERS_CHANGED).toast(null, "success", "Folders connected",
                    ids.size() + (ids.size() == 1 ? " folder is" : " folders are") + " now available to the agent.");
        }
        return triggers.on(Hx.empty()).build();
    }

    /**
     * Turns the chosen files into chips for the composer and closes the dialog. Only files in a connected folder are
     * accepted; anything else is ignored.
     */
    @POST
    @Path("/attach")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response attach(@RestForm("file") List<String> chosen) {
        Set<String> available = folders.connectedFiles().stream().map(CatalogFileDTO::name).collect(Collectors.toSet());
        List<FileRefDTO> files = (chosen == null ? List.<String>of() : chosen).stream()
                .filter(available::contains).distinct().map(name -> new FileRefDTO(name, "onedrive")).toList();
        Response.ResponseBuilder response = files.isEmpty() ? Hx.empty() : Hx.html(Templates.chips(files));
        return Hx.triggers().event(Hx.CLOSE_DIALOG).on(response).build();
    }
}
