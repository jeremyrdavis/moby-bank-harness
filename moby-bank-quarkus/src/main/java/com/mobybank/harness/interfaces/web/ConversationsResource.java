package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.AttachUploadCommand;
import com.mobybank.harness.application.CreateSessionCommand;
import com.mobybank.harness.application.FileRefDTO;
import com.mobybank.harness.application.MessageDTO;
import com.mobybank.harness.application.MoveSessionCommand;
import com.mobybank.harness.application.ResourceNotFoundException;
import com.mobybank.harness.application.SendMessageCommand;
import com.mobybank.harness.application.SessionApplicationService;
import com.mobybank.harness.application.SessionDTO;
import com.mobybank.harness.application.SessionSummaryDTO;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * The conversation fragments for {@code index.html}: the history list, the main pane for one conversation, sending a
 * message and moving the session. Requests that start background work wait for its outcome (see
 * {@link SessionWaiter}), because the page has no live channel. Those waits can last minutes, so every handler runs on
 * a virtual thread instead of holding one of the shared worker threads.
 *
 * <p>The server keeps no page state. Which conversation is open is a cookie, so a reload comes back to it.
 */
@Path("/ui/conversations")
@Produces(MediaType.TEXT_HTML)
@RunOnVirtualThread
public class ConversationsResource {

    private static final Logger LOG = Logger.getLogger(ConversationsResource.class);
    private static final String CURRENT_COOKIE = "moby-current";
    private static final String MOVE_TOAST = "move-cloud";

    private final SessionApplicationService sessions;
    private final SessionWaiter waiter;

    @Inject
    public ConversationsResource(SessionApplicationService sessions, SessionWaiter waiter) {
        this.sessions = sessions;
        this.waiter = waiter;
    }

    /** The sidebar history; the open conversation (or, failing that, the newest) is marked current. */
    @GET
    public String history(@CookieParam(CURRENT_COOKIE) String currentId) {
        List<SessionSummaryDTO> all = sessions.list();
        String current = all.stream().map(SessionSummaryDTO::id).filter(id -> id.equals(currentId)).findFirst()
                .orElse(all.isEmpty() ? null : all.get(0).id());
        Map<String, List<HistoryGroup.Row>> byGroup = new LinkedHashMap<>();
        for (SessionSummaryDTO session : all) {
            byGroup.computeIfAbsent(session.group(), group -> new ArrayList<>()).add(new HistoryGroup.Row(
                    session.id(), session.title(), "cloud".equals(session.location()), session.id().equals(current)));
        }
        List<HistoryGroup> groups = byGroup.entrySet().stream()
                .map(entry -> new HistoryGroup(entry.getKey(), entry.getValue())).toList();
        return Templates.history(groups).render();
    }

    /** The conversation the cookie names, else the most recent one, else a new one. */
    @GET
    @Path("/current")
    public Response current(@CookieParam(CURRENT_COOKIE) String currentId) {
        Optional<SessionDTO> open = find(currentId);
        if (open.isPresent()) {
            return main(open.get(), false);
        }
        List<SessionSummaryDTO> all = sessions.list();
        if (!all.isEmpty()) {
            return main(sessions.get(all.get(0).id()), false);
        }
        return main(sessions.create(new CreateSessionCommand(null)), true);
    }

    @GET
    @Path("/{id}")
    public Response open(@PathParam("id") String id) {
        return main(sessions.get(id), true);
    }

    /** Starts a new conversation. */
    @POST
    public Response create() {
        return main(sessions.create(new CreateSessionCommand(null)), true);
    }

    /**
     * Sends the analyst's message with any files and waits for the agent's reply, then answers with both messages.
     * Files come from the machine ({@code files}) or from OneDrive ({@code onedrive}, by name).
     */
    @POST
    @Path("/{id}/messages")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response send(@PathParam("id") String id,
                         @RestForm("text") String text,
                         @RestForm("files") List<FileUpload> files,
                         @RestForm("onedrive") List<String> onedrive) throws IOException {
        List<FileRefDTO> attachments = new ArrayList<>();
        for (FileUpload file : files == null ? List.<FileUpload>of() : files) {
            if (file.fileName() != null && !file.fileName().isBlank() && file.size() > 0) {
                attachments.add(sessions.attachUpload(
                        new AttachUploadCommand(id, file.fileName(), Files.readAllBytes(file.uploadedFile()))));
            }
        }
        for (String name : onedrive == null ? List.<String>of() : onedrive) {
            attachments.add(new FileRefDTO(name, "onedrive"));
        }

        MessageDTO[] sent = new MessageDTO[1];
        Optional<MessageDTO> reply = waiter.awaitReply(id,
                () -> sent[0] = sessions.sendMessage(new SendMessageCommand(id, text, attachments)));

        List<MessageDTO> messages = new ArrayList<>(List.of(sent[0]));
        Hx.Triggers triggers = Hx.triggers().event(Hx.CONVERSATIONS_CHANGED);
        if (reply.isPresent()) {
            messages.add(reply.get());
        } else {
            triggers.toast(null, null, "The agent is still working",
                    "It is taking longer than expected. Open the conversation again in a moment to see the reply.");
        }
        return triggers.on(Hx.html(Templates.sent(sessions.get(id), messages))).build();
    }

    /**
     * Moves the session to the other location ({@code to}, default {@code cloud}) and waits for the move to end. The
     * page raised a loading toast with id {@value #MOVE_TOAST} when it sent the request; this answer replaces it.
     */
    @POST
    @Path("/{id}/move")
    public Response move(@PathParam("id") String id, @QueryParam("to") @DefaultValue("cloud") String to) {
        Optional<SessionWaiter.MoveOutcome> outcome;
        try {
            outcome = waiter.awaitMove(id, to, () -> sessions.move(new MoveSessionCommand(id, to)));
        } catch (RuntimeException e) {
            LOG.warnf("Could not start moving session %s to %s: %s", id, to, e.getMessage());
            return moveToast("Could not move the session", e.getMessage());
        }
        if (outcome.isEmpty()) {
            return moveToast("The move is still running", "Open the conversation again in a moment to see where it runs.");
        }
        if (!outcome.get().completed()) {
            return moveToast("Move failed", outcome.get().detail());
        }
        boolean cloud = "cloud".equals(outcome.get().detail());
        Hx.Triggers triggers = Hx.triggers().event(Hx.CONVERSATIONS_CHANGED).toast(MOVE_TOAST, "success",
                cloud ? "Session moved to cloud" : "Session moved to local",
                cloud ? "Context and files are now on the cloud model. The local copy is kept."
                        : "The session runs on the local model again. The cloud copy is kept.");
        return triggers.on(withCookie(Hx.html(Templates.main(sessions.get(id))), id)).build();
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private Optional<SessionDTO> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(sessions.get(id));
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            return Optional.empty(); // a stale or foreign cookie
        }
    }

    private Response main(SessionDTO session, boolean refreshHistory) {
        Response.ResponseBuilder response = withCookie(Hx.html(Templates.main(session)), session.id());
        if (refreshHistory) {
            response = Hx.triggers().event(Hx.CONVERSATIONS_CHANGED).on(response);
        }
        return response.build();
    }

    private static Response moveToast(String title, String description) {
        return Hx.triggers().toast(MOVE_TOAST, "error", title, description)
                .on(Hx.empty().header("HX-Reswap", "none")).build();
    }

    private static Response.ResponseBuilder withCookie(Response.ResponseBuilder response, String sessionId) {
        return response.cookie(new NewCookie.Builder(CURRENT_COOKIE).value(sessionId).path("/")
                .sameSite(NewCookie.SameSite.LAX).build());
    }
}
