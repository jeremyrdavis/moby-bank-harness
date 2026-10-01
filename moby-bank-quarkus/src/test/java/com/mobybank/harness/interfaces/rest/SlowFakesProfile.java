package com.mobybank.harness.interfaces.rest;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** Makes the fake agent and transfer take long enough for a test to observe a busy session. */
public class SlowFakesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "harness.fake.agent-delay-ms", "1500",
                "harness.fake.move-packaging-ms", "1500",
                "harness.fake.move-transfer-ms", "0");
    }
}
