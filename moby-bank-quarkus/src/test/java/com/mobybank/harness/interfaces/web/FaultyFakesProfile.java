package com.mobybank.harness.interfaces.web;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** Fakes that lose a cloud sandbox after one turn and refuse to move a session back to local. */
public class FaultyFakesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "harness.fake.cloud-expiry-turns", "1",
                "harness.fake.move-failure", "to-local");
    }
}
