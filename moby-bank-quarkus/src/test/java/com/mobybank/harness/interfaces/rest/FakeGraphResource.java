package com.mobybank.harness.interfaces.rest;

import com.mobybank.harness.infrastructure.graph.FakeGraphServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/** Starts the fake Graph server before the app and points the app's configuration at it. */
public class FakeGraphResource implements QuarkusTestResourceLifecycleManager {

    static volatile FakeGraphServer server;

    @Override
    public Map<String, String> start() {
        server = FakeGraphServer.start();
        return Map.of(
                "harness.documents.mode", "graph",
                "harness.graph.base-url", server.baseUrl(),
                "harness.graph.access-token", FakeGraphServer.GOOD_TOKEN);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.close();
        }
    }
}
