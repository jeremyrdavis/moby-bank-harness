package com.mobybank.harness.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One message in a {@link Session}. Immutable, and identified by its id within the session. A user message carries
 * text and attached files; an assistant message carries the agent's trace, prose and optional table.
 */
public final class Message {

    private final MessageId id;
    private final MessageRole role;
    private final String text;
    private final List<FileRef> files;
    private final List<Step> steps;
    private final List<String> paragraphs;
    private final ResultTable table;
    private final Instant createdAt;

    private Message(MessageId id, MessageRole role, String text, List<FileRef> files, List<Step> steps,
                    List<String> paragraphs, ResultTable table, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "message id required");
        this.role = Objects.requireNonNull(role, "role required");
        this.text = Objects.requireNonNull(text, "text required");
        this.files = List.copyOf(files);
        this.steps = List.copyOf(steps);
        this.paragraphs = List.copyOf(paragraphs);
        this.table = table;
        this.createdAt = Objects.requireNonNull(createdAt, "created-at required");
    }

    static Message user(MessageId id, String text, List<FileRef> files, Instant at) {
        return new Message(id, MessageRole.USER, text, files, List.of(), List.of(), null, at);
    }

    static Message assistant(MessageId id, AgentReply reply, Instant at) {
        return new Message(id, MessageRole.ASSISTANT, "", List.of(), reply.steps(), reply.paragraphs(),
                reply.table().orElse(null), at);
    }

    /** Reconstruction path for the persistence layer. Domain code builds messages through {@link Session}. */
    public static Message rehydrate(MessageId id, MessageRole role, String text, List<FileRef> files,
                                    List<Step> steps, List<String> paragraphs, Optional<ResultTable> table,
                                    Instant createdAt) {
        return new Message(id, role, text, files, steps, paragraphs, table.orElse(null), createdAt);
    }

    public MessageId id() {
        return id;
    }

    public MessageRole role() {
        return role;
    }

    /** The user's text; empty for assistant messages. */
    public String text() {
        return text;
    }

    public List<FileRef> files() {
        return files;
    }

    public List<Step> steps() {
        return steps;
    }

    /** The agent's prose; empty for user messages. */
    public List<String> paragraphs() {
        return paragraphs;
    }

    public Optional<ResultTable> table() {
        return Optional.ofNullable(table);
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Message message && id.equals(message.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
