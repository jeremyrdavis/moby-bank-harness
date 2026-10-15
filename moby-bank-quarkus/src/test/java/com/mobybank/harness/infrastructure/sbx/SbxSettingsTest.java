package com.mobybank.harness.infrastructure.sbx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SbxSettingsTest {

    private static SbxSettings with(String onTimeout) {
        return new SbxSettings("claude", List.of(), "/home/agent/workspace", Duration.ofMinutes(10),
                Duration.ofHours(1), Duration.ofHours(8), onTimeout);
    }

    @Test
    void acceptsTheThreeThingsTheServiceCanDoOnTimeout() {
        assertEquals("stop", with("stop").cloudOnTimeout());
        assertEquals("delete", with(" delete ").cloudOnTimeout());
        assertEquals("restart", with("restart").cloudOnTimeout());
    }

    @Test
    void blankMeansTheServiceDefault() {
        assertNull(with("").cloudOnTimeout());
        assertNull(with(null).cloudOnTimeout());
    }

    @Test
    void rejectsAnythingElseNamingTheSetting() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> with("pause"));
        assertEquals("harness.sbx.cloud-on-timeout must be one of [stop, delete, restart] but was 'pause'",
                e.getMessage());
    }

    @Test
    void theDefaultAgentArgumentsWorkInBothLocationsAndKeepConnectorsOut() {
        assertEquals("--dangerously-skip-permissions --model claude-opus-5-5 --strict-mcp-config",
                SbxAdapters.DEFAULT_AGENT_ARGS);
    }
}
