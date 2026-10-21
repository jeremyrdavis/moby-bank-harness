package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.infrastructure.graph.FakeEntraServer;
import com.mobybank.harness.infrastructure.graph.FakeGraphServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts a fake Microsoft Entra and a fake Microsoft Graph and points the application at them: sign-in with Microsoft is
 * on, OneDrive is read through Graph as each user, and there is no app-wide Graph credential.
 */
public class MicrosoftFakes implements QuarkusTestResourceLifecycleManager {

    static volatile FakeEntraServer entra;
    static volatile FakeGraphServer graph;

    @Override
    public Map<String, String> start() {
        entra = FakeEntraServer.start();
        graph = FakeGraphServer.start();
        Map<String, String> config = new HashMap<>();
        config.put("harness.auth.mode", "login");
        config.put("harness.auth.microsoft.enabled", "true");
        config.put("quarkus.oidc.tenant-enabled", "true");
        config.put("quarkus.oidc.auth-server-url", entra.authority());
        config.put("quarkus.oidc.client-id", FakeEntraServer.CLIENT_ID);
        config.put("quarkus.oidc.credentials.secret", FakeEntraServer.CLIENT_SECRET);
        config.put("quarkus.http.auth.permission.microsoft.enabled", "true");
        config.put("harness.documents.mode", "graph");
        config.put("harness.graph.base-url", graph.baseUrl());
        return config;
    }

    @Override
    public void stop() {
        entra.close();
        graph.close();
    }
}
