package com.mobybank.harness.interfaces.web;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** A slow agent and a short wait, so a request gives up before the work is done. */
public class ShortWaitProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "harness.ui.wait-timeout", "300ms",
                "harness.fake.agent-delay-ms", "1500",
                "harness.fake.move-packaging-ms", "1500",
                "harness.fake.move-transfer-ms", "0");
    }
}
