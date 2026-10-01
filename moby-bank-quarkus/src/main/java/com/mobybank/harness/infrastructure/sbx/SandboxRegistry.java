package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SessionId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which sandbox currently backs each session, and the agent's own conversation id inside it so the next
 * turn can resume. Sandbox names follow {@code harness-<session>-g<generation>}; each move creates the next
 * generation, which lets the adapters find a session's sandbox again after a restart by listing sandboxes.
 */
@ApplicationScoped
public class SandboxRegistry {

    /** The sandbox a session runs in now. {@code name} is exactly as {@code sbx ls} lists it. */
    public record Handle(Location location, String name, int generation) {
    }

    private final Map<SessionId, Handle> handles = new ConcurrentHashMap<>();
    private final Map<SessionId, String> agentSessions = new ConcurrentHashMap<>();

    public Optional<Handle> current(SessionId session) {
        return Optional.ofNullable(handles.get(session));
    }

    public void set(SessionId session, Handle handle) {
        handles.put(session, handle);
    }

    public Optional<String> agentSession(SessionId session) {
        return Optional.ofNullable(agentSessions.get(session));
    }

    public void setAgentSession(SessionId session, String agentSessionId) {
        agentSessions.put(session, agentSessionId);
    }

    public void forgetAgentSession(SessionId session) {
        agentSessions.remove(session);
    }

    /** The name prefix shared by every generation of a session's sandbox. */
    static String baseName(SessionId session) {
        return "harness-" + session.value().toString().replace("-", "").substring(0, 12);
    }

    static String nameFor(SessionId session, int generation) {
        return baseName(session) + "-g" + generation;
    }

    /**
     * Reads the generation out of a name listed by {@code sbx ls}, which may carry an {@code agent/} prefix and, for
     * cloud sandboxes, a suffix the service added after {@code -g<generation>}.
     */
    static Optional<Integer> generationOf(String listedName, SessionId session) {
        String name = listedName.substring(listedName.lastIndexOf('/') + 1).strip();
        String prefix = baseName(session) + "-g";
        if (!name.startsWith(prefix)) {
            return Optional.empty();
        }
        String rest = name.substring(prefix.length());
        int end = 0;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
            end++;
        }
        if (end == 0 || end > 6 || (end < rest.length() && rest.charAt(end) != '-')) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(rest.substring(0, end)));
    }
}
