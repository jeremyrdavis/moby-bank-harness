package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.application.AttachUploadCommand;
import com.mobybank.harness.application.CreateSessionCommand;
import com.mobybank.harness.application.FileRefDTO;
import com.mobybank.harness.application.MessageDTO;
import com.mobybank.harness.application.MoveSessionCommand;
import com.mobybank.harness.application.SendMessageCommand;
import com.mobybank.harness.application.SessionApplicationService;
import com.mobybank.harness.application.SessionDTO;
import com.mobybank.harness.application.SessionEvent;
import com.mobybank.harness.application.SessionEventStream;
import com.mobybank.harness.application.SessionSummaryDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Sessions")
public class SessionsResource {

    private static final Duration KEEP_ALIVE = Duration.ofSeconds(20);

    private final SessionApplicationService sessions;
    private final SessionEventStream events;

    @Inject
    public SessionsResource(SessionApplicationService sessions, SessionEventStream events) {
        this.sessions = sessions;
        this.events = events;
    }

    @GET
    @Operation(summary = "List the conversation history",
            description = "Most recently updated first. Each entry carries the recency group shown in the sidebar.")
    public List<SessionSummaryDTO> list() {
        return sessions.list();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start a new conversation")
    @APIResponse(responseCode = "201", description = "The new, empty session",
            content = @Content(schema = @Schema(implementation = SessionDTO.class)))
    @APIResponse(responseCode = "400", description = "Unknown location",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response create(CreateSessionRequest request, @Context UriInfo uriInfo) {
        SessionDTO created = sessions.create(new CreateSessionCommand(request == null ? null : request.location()));
        return Response.created(uriInfo.getAbsolutePathBuilder().path(created.id()).build()).entity(created).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Open a conversation", description = "The session with all its messages and shared files.")
    @APIResponse(responseCode = "404", description = "No such session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public SessionDTO get(@PathParam("id") String id) {
        return sessions.get(id);
    }

    @POST
    @Path("/{id}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Send a message",
            description = "Stores the message and starts the agent on it. Returns immediately with the stored "
                    + "message; follow the agent's steps and reply on the session's event stream.")
    @APIResponse(responseCode = "202", description = "The stored user message; the agent is working",
            content = @Content(schema = @Schema(implementation = MessageDTO.class)))
    @APIResponse(responseCode = "400", description = "No text and no files, or an invalid attachment",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @APIResponse(responseCode = "404", description = "No such session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @APIResponse(responseCode = "409", description = "The session is busy (the agent is working or it is moving)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response sendMessage(@PathParam("id") String id, SendMessageRequest request) {
        SendMessageRequest body = request == null ? new SendMessageRequest(null, null) : request;
        MessageDTO stored = sessions.sendMessage(new SendMessageCommand(id, body.text(), body.files()));
        return Response.accepted(stored).build();
    }

    @POST
    @Path("/{id}/uploads")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload a file from the analyst's machine",
            description = "Stores the file for this session. Attach it to a message by sending "
                    + "{\"name\": <returned name>, \"source\": \"upload\"} in the message's files.")
    @APIResponse(responseCode = "201", description = "The stored file reference",
            content = @Content(schema = @Schema(implementation = FileRefDTO.class)))
    @APIResponse(responseCode = "400", description = "No file, or an invalid file name",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @APIResponse(responseCode = "404", description = "No such session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response upload(@PathParam("id") String id,
                           @RestForm("file") @Parameter(description = "The file to upload") FileUpload file)
            throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("multipart field 'file' is required");
        }
        byte[] content = Files.readAllBytes(file.uploadedFile());
        FileRefDTO stored = sessions.attachUpload(new AttachUploadCommand(id, file.fileName(), content));
        return Response.status(Response.Status.CREATED).entity(stored).build();
    }

    @POST
    @Path("/{id}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Move the session to the other location",
            description = "Starts moving the session between the local and the cloud sandbox, in either "
                    + "direction. Returns at once with the session in the moving state; progress and the "
                    + "outcome arrive on the event stream.")
    @APIResponse(responseCode = "202", description = "The session, now moving",
            content = @Content(schema = @Schema(implementation = SessionDTO.class)))
    @APIResponse(responseCode = "400", description = "Missing or unknown target",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @APIResponse(responseCode = "404", description = "No such session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @APIResponse(responseCode = "409",
            description = "The session is busy, or already runs at the target location",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response move(@PathParam("id") String id, MoveSessionRequest request) {
        SessionDTO moving = sessions.move(new MoveSessionCommand(id, request == null ? null : request.target()));
        return Response.accepted(moving).build();
    }

    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(summary = "Follow a session live (server-sent events)",
            description = "Streams what happens to the session. The first event, `ready`, confirms the "
                    + "subscription is active, so open the stream before sending a message or starting a move. "
                    + "Only events published after subscribing are delivered; read the session to catch up. "
                    + "Each event's name is the SSE `event:` field and its `data:` is JSON:\n\n"
                    + "| event | data |\n|---|---|\n"
                    + "| `ready` | `{\"sessionId\"}` |\n"
                    + "| `thinking` | `{\"sessionId\", \"label\"}`: the agent started; label is e.g. \"Reading 2 files on local model…\" |\n"
                    + "| `step` | `{\"sessionId\", \"step\": {\"kind\", \"label\"}}`: a file read or calculation, as it happens |\n"
                    + "| `message` | `{\"sessionId\", \"message\": <Message>}`: a message joined the conversation (the user's, then the agent's) |\n"
                    + "| `move-progress` | `{\"sessionId\", \"stage\", \"detail\"}`: stage is `packaging` or `transferring` |\n"
                    + "| `moved` | `{\"sessionId\", \"location\"}`: the move finished; the session now runs at location |\n"
                    + "| `move-failed` | `{\"sessionId\", \"reason\"}`: the move failed and the session stayed where it was |\n\n"
                    + "A comment line (`: keep-alive`) is sent every 20 seconds to keep the connection open.")
    @APIResponse(responseCode = "200", description = "An event stream",
            content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
    @APIResponse(responseCode = "404", description = "No such session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Multi<OutboundSseEvent> events(@PathParam("id") String id, @Context Sse sse) {
        String sessionId = sessions.get(id).id(); // rejects unknown ids and gives the canonical form

        Multi<OutboundSseEvent> live = Multi.createFrom().emitter(emitter -> {
            SessionEventStream.Subscription subscription = events.subscribe(sessionId, event -> emitter.emit(toSse(sse, event)));
            emitter.onTermination(subscription::close);
            emitter.emit(sse.newEventBuilder().name("ready").mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(new Ready(sessionId)).build());
        }, BackPressureStrategy.BUFFER);

        Multi<OutboundSseEvent> keepAlive = Multi.createFrom().ticks().every(KEEP_ALIVE)
                .map(tick -> sse.newEventBuilder().comment("keep-alive").build());

        return Multi.createBy().merging().streams(live, keepAlive);
    }

    private static OutboundSseEvent toSse(Sse sse, SessionEvent event) {
        return sse.newEventBuilder().name(event.type()).mediaType(MediaType.APPLICATION_JSON_TYPE).data(event).build();
    }

    /** Payload of the first event on every stream. */
    public record Ready(String sessionId) {
    }
}
