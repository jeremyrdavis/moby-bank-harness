package com.mobybank.harness.domain;

import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of one turn handed to the sandbox agent: where the session runs, the conversation so far,
 * the new user message and every file shared anywhere in the conversation. A snapshot, not the aggregate, because the
 * agent works outside any aggregate update. The shared files let an agent that starts in a fresh sandbox copy in what
 * the analyst attached earlier.
 */
public record AgentTurn(SessionId sessionId, Location location, List<Message> history, Message userMessage,
                        List<FileRef> sessionFiles) {

    public AgentTurn {
        Objects.requireNonNull(sessionId, "session id required");
        Objects.requireNonNull(location, "location required");
        Objects.requireNonNull(history, "history required");
        Objects.requireNonNull(userMessage, "user message required");
        Objects.requireNonNull(sessionFiles, "session files required");
        history = List.copyOf(history);
        sessionFiles = List.copyOf(sessionFiles);
        if (userMessage.role() != MessageRole.USER) {
            throw new IllegalArgumentException("a turn starts from a user message");
        }
    }

    /** A turn whose only files are the ones attached to the new message. */
    public AgentTurn(SessionId sessionId, Location location, List<Message> history, Message userMessage) {
        this(sessionId, location, history, userMessage, userMessage.files());
    }
}
