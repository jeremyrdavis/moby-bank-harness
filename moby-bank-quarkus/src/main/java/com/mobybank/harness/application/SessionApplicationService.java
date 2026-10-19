package com.mobybank.harness.application;

import com.mobybank.harness.application.SessionEvent.MessageAdded;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEvent.MoveFailed;
import com.mobybank.harness.application.SessionEvent.MoveProgressed;
import com.mobybank.harness.application.SessionEvent.StepRecorded;
import com.mobybank.harness.application.SessionEvent.Thinking;
import com.mobybank.harness.domain.AgentRepliedEvent;
import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.DomainEvent;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Message;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.RecencyGroup;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionMovedEvent;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.SessionStatus;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;

/**
 * Orchestrates the session use cases: start a conversation, send a message, attach an upload, and move a session
 * between the local and cloud sandbox. Each step loads the session, changes it, saves it, then publishes what
 * happened. Agent turns and moves run in the background; callers follow progress through {@link SessionEventStream}.
 */
@ApplicationScoped
public class SessionApplicationService {

    private final SessionRepository sessions;
    private final SandboxAgent agent;
    private final SandboxTransfer transfer;
    private final UploadStore uploads;
    private final SessionEventStream events;
    private final BackgroundRunner runner;
    private final Clock clock;

    @Inject
    public SessionApplicationService(SessionRepository sessions, SandboxAgent agent, SandboxTransfer transfer,
                                     UploadStore uploads, SessionEventStream events, BackgroundRunner runner,
                                     Clock clock) {
        this.sessions = sessions;
        this.agent = agent;
        this.transfer = transfer;
        this.uploads = uploads;
        this.events = events;
        this.runner = runner;
        this.clock = clock;
    }

    public SessionDTO create(CreateSessionCommand command) {
        Location location = Dtos.location(command == null ? null : command.location(), Location.LOCAL);
        Session session = Session.start(SessionId.fresh(), location, clock.instant());
        sessions.persist(session);
        Log.debugf("Started session %s at %s", Dtos.id(session.id()), location);
        return toDto(session);
    }

    /** Chooses where a session that has not started yet runs. No sandbox exists yet, so nothing is moved. */
    public SessionDTO launch(LaunchSessionCommand command) {
        SessionId id = Dtos.sessionId(command.sessionId());
        Location location = Dtos.location(command.location(), null);
        Session session = load(id);
        session.launchAt(location, clock.instant());
        sessions.persist(session);
        Log.debugf("Session %s will run at %s (chosen before it started)", Dtos.id(id), location);
        return toDto(session);
    }

    /** The conversation history, most recently updated first. */
    public List<SessionSummaryDTO> list() {
        List<SessionSummaryDTO> history = sessions.findAllByMostRecentlyUpdated().stream()
                .map(session -> Dtos.summary(session, group(session)))
                .toList();
        Log.debugf("Listed %d sessions", history.size());
        return history;
    }

    public SessionDTO get(String sessionId) {
        Log.tracef("Opening session %s", sessionId);
        return toDto(load(Dtos.sessionId(sessionId)));
    }

    /**
     * Adds the analyst's message and starts the agent on it in the background. Returns the message as stored;
     * the agent's steps and reply arrive as events.
     */
    public MessageDTO sendMessage(SendMessageCommand command) {
        SessionId id = Dtos.sessionId(command.sessionId());
        Session session = load(id);
        List<FileRef> files = Dtos.fileRefs(command.files());
        Log.debugf("Message for session %s at %s: %d chars, %d attached files, %d earlier messages",
                Dtos.id(id), session.location(), command.text() == null ? 0 : command.text().length(), files.size(),
                session.messages().size());
        List<Message> history = session.messages();
        Message userMessage = session.postUserMessage(command.text() == null ? "" : command.text(), files,
                clock.instant());
        sessions.persist(session);

        MessageDTO stored = Dtos.message(userMessage);
        events.publish(new MessageAdded(Dtos.id(id), stored));
        events.publish(new Thinking(Dtos.id(id), thinkingLabel(session.location(), files.size())));

        AgentTurn turn = new AgentTurn(id, session.location(), history, userMessage,
                session.filesShared());
        Log.debugf("Session %s is %s; starting the agent turn in the background (%d files shared so far)",
                Dtos.id(id), session.status(), turn.sessionFiles().size());
        runner.run(() -> runTurn(turn));
        return stored;
    }

    /** Stores a file the analyst uploaded so it can be attached to the next message. */
    public FileRefDTO attachUpload(AttachUploadCommand command) {
        SessionId id = Dtos.sessionId(command.sessionId());
        load(id);
        if (command.content() == null) {
            throw new IllegalArgumentException("file content required");
        }
        FileRef ref = new FileRef(baseName(command.fileName()), FileSource.UPLOAD);
        uploads.store(id, ref.name(), command.content());
        Log.debugf("Stored an upload for session %s: %d bytes", Dtos.id(id), command.content().length);
        return Dtos.file(ref);
    }

    /** Keeps only the file name, so a client-supplied path can never point outside the session's upload area. */
    private static String baseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("file name required");
        }
        String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("invalid file name: " + fileName);
        }
        return name;
    }

    /**
     * Starts moving the session to the other location and returns it in the {@code moving} state. Works in both
     * directions. Progress and the outcome arrive as events.
     */
    public SessionDTO move(MoveSessionCommand command) {
        SessionId id = Dtos.sessionId(command.sessionId());
        Location target = Dtos.location(command.target(), null);
        Session session = load(id);
        Location from = session.location();
        Log.debugf("Move requested for session %s: %s -> %s (status %s)", Dtos.id(id), from, target,
                session.status());
        session.beginMove(target, clock.instant());
        sessions.persist(session);

        MoveRequest request = new MoveRequest(id, from, target, session.messages(), session.filesShared());
        Log.debugf("Session %s is %s; starting the move in the background (%d messages, %d files)",
                Dtos.id(id), session.status(), request.history().size(), request.files().size());
        runner.run(() -> runMove(request));
        return toDto(session);
    }

    // --- background work ---------------------------------------------------------------------------------------

    private void runTurn(AgentTurn turn) {
        String sessionId = Dtos.id(turn.sessionId());
        long started = System.nanoTime();
        Log.debugf("Agent turn started for session %s at %s", sessionId, turn.location());
        try {
            AgentReply reply = agent.runTurn(turn, step -> {
                Log.debugf("Agent step for session %s: %s", sessionId, step.kind());
                events.publish(new StepRecorded(sessionId, Dtos.step(step)));
            });
            Log.debugf("Agent turn finished for session %s in %d ms: %d steps, %d paragraphs, table=%s, notice=%s",
                    sessionId, elapsedMs(started), reply.steps().size(), reply.paragraphs().size(),
                    reply.table().isPresent(), reply.notice().isPresent());
            Session session = load(turn.sessionId());
            session.recordAgentReply(reply, clock.instant());
            sessions.persist(session);
            publish(session);
        } catch (RuntimeException e) {
            Log.errorf(e, "Agent turn failed for session %s after %d ms", sessionId, elapsedMs(started));
            failTurn(turn.sessionId(), e);
        }
    }

    private void failTurn(SessionId id, RuntimeException cause) {
        try {
            Session session = load(id);
            if (session.status() != SessionStatus.RUNNING) {
                Log.debugf("Not recording a failure for session %s: it is %s, not running", Dtos.id(id),
                        session.status());
                return;
            }
            Message failure = session.recordAgentFailure(String.valueOf(cause.getMessage()), clock.instant());
            sessions.persist(session);
            Log.debugf("Recorded the agent failure for session %s", Dtos.id(id));
            events.publish(new MessageAdded(Dtos.id(id), Dtos.message(failure)));
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not record the agent failure for session %s", Dtos.id(id));
        }
    }

    private void runMove(MoveRequest request) {
        String sessionId = Dtos.id(request.sessionId());
        long started = System.nanoTime();
        Log.debugf("Move started for session %s: %s -> %s", sessionId, request.from(), request.to());
        try {
            transfer.move(request, progress -> {
                Log.debugf("Move progress for session %s: %s", sessionId, progress.stage());
                events.publish(new MoveProgressed(sessionId, Dtos.name(progress.stage()), progress.detail()));
            });
            Log.debugf("Move finished for session %s in %d ms", sessionId, elapsedMs(started));
            Session session = load(request.sessionId());
            session.completeMove(clock.instant());
            sessions.persist(session);
            publish(session);
        } catch (RuntimeException e) {
            Log.errorf(e, "Move failed for session %s after %d ms", sessionId, elapsedMs(started));
            failMove(request.sessionId(), e);
        }
    }

    private void failMove(SessionId id, RuntimeException cause) {
        try {
            Session session = load(id);
            if (session.status() != SessionStatus.MOVING) {
                Log.debugf("Not aborting the move for session %s: it is %s, not moving", Dtos.id(id), session.status());
                return;
            }
            session.abortMove(clock.instant());
            sessions.persist(session);
            Log.debugf("Aborted the move for session %s; it stays at %s", Dtos.id(id), session.location());
            events.publish(new MoveFailed(Dtos.id(id), String.valueOf(cause.getMessage())));
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not abort the move for session %s", Dtos.id(id));
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    /** Drains the events the aggregate raised and publishes them as session events. */
    private void publish(Session session) {
        for (DomainEvent event : session.pullPendingEvents()) {
            Log.debugf("Publishing %s for session %s", event.getClass().getSimpleName(), Dtos.id(session.id()));
            switch (event) {
                case AgentRepliedEvent replied -> session.messages().stream()
                        .filter(message -> message.id().equals(replied.messageId()))
                        .findFirst()
                        .ifPresent(message -> events.publish(
                                new MessageAdded(Dtos.id(session.id()), Dtos.message(message))));
                case SessionMovedEvent moved ->
                        events.publish(new MoveCompleted(Dtos.id(session.id()), Dtos.name(moved.to())));
            }
        }
    }

    private Session load(SessionId id) {
        return sessions.findById(id).orElseThrow(() -> {
            Log.debugf("Session %s not found", Dtos.id(id));
            return new ResourceNotFoundException("session", Dtos.id(id));
        });
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private SessionDTO toDto(Session session) {
        return Dtos.session(session, group(session));
    }

    private String group(Session session) {
        return RecencyGroup.of(session.updatedAt(), clock.instant(), clock.getZone()).label();
    }

    private static String thinkingLabel(Location location, int fileCount) {
        String where = location == Location.CLOUD ? "cloud model" : "local model";
        if (fileCount == 0) {
            return "Working on " + where + "…";
        }
        return "Reading " + fileCount + (fileCount == 1 ? " file" : " files") + " on " + where + "…";
    }
}
