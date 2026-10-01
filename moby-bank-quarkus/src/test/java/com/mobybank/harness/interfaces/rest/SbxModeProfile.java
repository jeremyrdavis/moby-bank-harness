package com.mobybank.harness.interfaces.rest;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** Runs the app with the real sbx adapters pointed at a program that does not exist. */
public class SbxModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "harness.sandbox.mode", "sbx",
                "harness.sbx.binary", "sbx-binary-that-is-not-installed");
    }
}
