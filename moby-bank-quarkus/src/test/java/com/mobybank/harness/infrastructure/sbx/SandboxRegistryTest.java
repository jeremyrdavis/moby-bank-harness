package com.mobybank.harness.infrastructure.sbx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SandboxRegistryTest {

    static final SessionId SESSION = SessionId.parse("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    static final SessionId OTHER = SessionId.parse("ffffffff-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    @Test
    void namesAreDerivedFromTheSessionAndGeneration() {
        assertEquals("harness-0a1b2c3d4e5f", SandboxRegistry.baseName(SESSION));
        assertEquals("harness-0a1b2c3d4e5f-g0", SandboxRegistry.nameFor(SESSION, 0));
        assertEquals("harness-0a1b2c3d4e5f-g12", SandboxRegistry.nameFor(SESSION, 12));
    }

    @Test
    void generationsAreReadBackFromListedNamesIncludingCloudDecorations() {
        assertEquals(Optional.of(0), SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-g0", SESSION));
        assertEquals(Optional.of(12), SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-g12", SESSION));
        assertEquals(Optional.of(3), SandboxRegistry.generationOf("claude/harness-0a1b2c3d4e5f-g3-k3j2x", SESSION));
        assertEquals(Optional.of(1), SandboxRegistry.generationOf("  harness-0a1b2c3d4e5f-g1-ab12  ", SESSION));
    }

    @Test
    void otherSessionsAndOtherNamesAreNotMatched() {
        assertTrue(SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-g1", OTHER).isEmpty());
        assertTrue(SandboxRegistry.generationOf("my-own-sandbox", SESSION).isEmpty());
        assertTrue(SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-g", SESSION).isEmpty());
        assertTrue(SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-gx1", SESSION).isEmpty());
        assertTrue(SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-g1x", SESSION).isEmpty());
        assertTrue(SandboxRegistry.generationOf("harness-0a1b2c3d4e5f-g99999999999", SESSION).isEmpty());
        assertTrue(SandboxRegistry.generationOf("harness-0a1b2c3d4e5f2-g1", SESSION).isEmpty());
    }

    @Test
    void handlesAndAgentSessionsAreRememberedPerSession() {
        SandboxRegistry registry = new SandboxRegistry();
        assertTrue(registry.current(SESSION).isEmpty());

        Handle handle = new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g0", 0);
        registry.set(SESSION, handle);
        registry.setAgentSession(SESSION, "agent-1");

        assertEquals(Optional.of(handle), registry.current(SESSION));
        assertEquals(Optional.of("agent-1"), registry.agentSession(SESSION));
        assertTrue(registry.current(OTHER).isEmpty());

        registry.forgetAgentSession(SESSION);
        assertTrue(registry.agentSession(SESSION).isEmpty());
        assertEquals(Optional.of(handle), registry.current(SESSION));
    }
}
