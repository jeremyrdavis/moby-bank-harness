package com.mobybank.harness.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A conversation between an analyst and the agent, and the aggregate root for its messages. A session runs in one
 * {@link Location} at a time and is either idle, running a turn, or moving. Only one of those can happen at once.
 */
public class Session {

    static final String DEFAULT_ATTACHMENT_PROMPT = "Review the attached files.";

    private final SessionId id;
    private long version;
    private SessionTitle title;
    private Location location;
    private SessionStatus status;
    private Location moveTarget;
    private final List<Message> messages;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Session(SessionId id, SessionTitle title, Location location, SessionStatus status, Location moveTarget,
                    List<Message> messages, Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.title = title;
        this.location = location;
        this.status = status;
        this.moveTarget = moveTarget;
        this.messages = new ArrayList<>(messages);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** Starts an empty session at the given location. */
    public static Session start(SessionId id, Location location, Instant now) {
        Objects.requireNonNull(id, "session id required");
        Objects.requireNonNull(location, "location required");
        Objects.requireNonNull(now, "now required");
        return new Session(id, SessionTitle.NEW_CONVERSATION, location, SessionStatus.IDLE, null, List.of(), now, now,
                0L);
    }

    /**
     * Reconstruction path for the persistence layer. It trusts stored state and raises no events. Domain code never
     * calls it.
     */
    public static Session rehydrate(SessionId id, SessionTitle title, Location location, SessionStatus status,
                                    Location moveTarget, List<Message> messages, Instant createdAt, Instant updatedAt,
                                    long version) {
        return new Session(id, title, location, status, moveTarget, messages, createdAt, updatedAt, version);
    }

    /**
     * Adds the analyst's message and marks the session as running. The first message also names the session. A
     * message with no text and only files gets a default prompt.
     *
     * @throws SessionBusyException if the session is not idle
     */
    public Message postUserMessage(String text, List<FileRef> files, Instant now) {
        Objects.requireNonNull(text, "text required");
        Objects.requireNonNull(files, "files required");
        Objects.requireNonNull(now, "now required");
        requireIdle();
        String trimmed = text.strip();
        if (trimmed.isEmpty() && files.isEmpty()) {
            throw new IllegalArgumentException("a message needs text or at least one file");
        }
        if (messages.isEmpty()) {
            title = new SessionTitle(trimmed.isEmpty() ? files.get(0).name() : trimmed);
        }
        Message message = Message.user(MessageId.fresh(), trimmed.isEmpty() ? DEFAULT_ATTACHMENT_PROMPT : trimmed,
                files, now);
        messages.add(message);
        status = SessionStatus.RUNNING;
        updatedAt = now;
        return message;
    }

    /** Records the agent's answer to the running turn and makes the session idle again. */
    public Message recordAgentReply(AgentReply reply, Instant now) {
        Objects.requireNonNull(reply, "reply required");
        Objects.requireNonNull(now, "now required");
        requireRunning();
        Message message = Message.assistant(MessageId.fresh(), reply, now);
        messages.add(message);
        status = SessionStatus.IDLE;
        updatedAt = now;
        pendingEvents.add(new AgentRepliedEvent(id, message.id()));
        return message;
    }

    /** Records that the agent could not answer the running turn, so the analyst sees why, and frees the session. */
    public Message recordAgentFailure(String reason, Instant now) {
        Objects.requireNonNull(reason, "reason required");
        Objects.requireNonNull(now, "now required");
        requireRunning();
        String detail = reason.isBlank() ? "no further detail" : reason.strip();
        Message message = Message.assistant(MessageId.fresh(),
                new AgentReply(List.of(), List.of("The agent could not complete this request: " + detail),
                        Optional.empty()),
                now);
        messages.add(message);
        status = SessionStatus.IDLE;
        updatedAt = now;
        return message;
    }

    /**
     * Starts moving the session to the other location. The target must differ from the current location, in
     * either direction.
     *
     * @throws SessionBusyException if the session is not idle
     * @throws InvalidMoveException if the target is the current location
     */
    public void beginMove(Location target, Instant now) {
        Objects.requireNonNull(target, "target required");
        Objects.requireNonNull(now, "now required");
        requireIdle();
        if (target == location) {
            throw new InvalidMoveException(id, location, target, "the session already runs there");
        }
        moveTarget = target;
        status = SessionStatus.MOVING;
        updatedAt = now;
    }

    /** Finishes the move: the session now runs at the target location. */
    public void completeMove(Instant now) {
        Objects.requireNonNull(now, "now required");
        if (status != SessionStatus.MOVING) {
            throw new IllegalStateException("session " + id.value() + " is not moving");
        }
        Location from = location;
        location = moveTarget;
        moveTarget = null;
        status = SessionStatus.IDLE;
        updatedAt = now;
        pendingEvents.add(new SessionMovedEvent(id, from, location));
    }

    /** Gives up on the move: the session stays where it was and becomes idle again. */
    public void abortMove(Instant now) {
        Objects.requireNonNull(now, "now required");
        if (status != SessionStatus.MOVING) {
            throw new IllegalStateException("session " + id.value() + " is not moving");
        }
        moveTarget = null;
        status = SessionStatus.IDLE;
        updatedAt = now;
    }

    /** The distinct files shared anywhere in this conversation, in the order they first appeared. */
    public List<FileRef> filesShared() {
        LinkedHashSet<FileRef> shared = new LinkedHashSet<>();
        messages.forEach(message -> shared.addAll(message.files()));
        return List.copyOf(shared);
    }

    public SessionId id() {
        return id;
    }

    public SessionTitle title() {
        return title;
    }

    public Location location() {
        return location;
    }

    public SessionStatus status() {
        return status;
    }

    /** The location the session is moving to, or null when it is not moving. */
    public Location moveTarget() {
        return moveTarget;
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    /** Drained by the application service after persisting, then published. */
    public List<DomainEvent> pullPendingEvents() {
        List<DomainEvent> drained = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    private void requireIdle() {
        if (status != SessionStatus.IDLE) {
            throw new SessionBusyException(id, status);
        }
    }

    private void requireRunning() {
        if (status != SessionStatus.RUNNING) {
            throw new IllegalStateException("session " + id.value() + " is not running a turn");
        }
    }
}
