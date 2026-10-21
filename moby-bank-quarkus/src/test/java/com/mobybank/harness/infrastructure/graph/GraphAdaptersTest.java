package com.mobybank.harness.infrastructure.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GraphAdaptersTest {

    @Test
    void theTokenEndpointOfATenantIsDerivedFromItsAuthorityUrl() {
        assertEquals("https://login.microsoftonline.com/t-1/oauth2/v2.0/token",
                GraphAdapters.tokenUrlOf("https://login.microsoftonline.com/t-1/v2.0"));
        assertEquals("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                GraphAdapters.tokenUrlOf("https://login.microsoftonline.com/consumers/v2.0/"));
        assertEquals("https://login.microsoftonline.com/common/oauth2/v2.0/token",
                GraphAdapters.tokenUrlOf(" https://login.microsoftonline.com/common/v2.0 "));
    }
}
