package com.mobybank.harness.application;

/**
 * Something that happened to a session, pushed to clients as it happens. {@link #type()} is the event name on the
 * wire. Payloads use API types only.
 */
public sealed interface SessionEvent {

    String sessionId();

    String type();

    /** The agent started working on a turn. */
    record Thinking(String sessionId, String label) implements SessionEvent {
        @Override
        public String type() {
            return "thinking";
        }
    }

    /** The agent read a file or ran a calculation. */
    record StepRecorded(String sessionId, StepDTO step) implements SessionEvent {
        @Override
        public String type() {
            return "step";
        }
    }

    /** A message joined the conversation. */
    record MessageAdded(String sessionId, MessageDTO message) implements SessionEvent {
        @Override
        public String type() {
            return "message";
        }
    }

    /** A move advanced to a new stage ({@code packaging} or {@code transferring}). */
    record MoveProgressed(String sessionId, String stage, String detail) implements SessionEvent {
        @Override
        public String type() {
            return "move-progress";
        }
    }

    /** The move finished; the session now runs at {@code location}. */
    record MoveCompleted(String sessionId, String location) implements SessionEvent {
        @Override
        public String type() {
            return "moved";
        }
    }

    /** The move failed and the session stayed where it was. */
    record MoveFailed(String sessionId, String reason) implements SessionEvent {
        @Override
        public String type() {
            return "move-failed";
        }
    }
}
