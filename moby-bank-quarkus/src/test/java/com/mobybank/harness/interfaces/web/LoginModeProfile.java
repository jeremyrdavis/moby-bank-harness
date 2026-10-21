package com.mobybank.harness.interfaces.web;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** Turns sign-in on, which the other tests leave off. */
public class LoginModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("harness.auth.mode", "login");
    }
}
