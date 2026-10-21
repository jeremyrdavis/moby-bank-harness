package com.mobybank.harness.interfaces.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MicrosoftSignInCheckTest {

    private static MicrosoftSignInCheck check(boolean enabled, String url, String clientId) {
        return new MicrosoftSignInCheck(enabled, Optional.ofNullable(url), Optional.ofNullable(clientId));
    }

    @Test
    void nothingIsCheckedWhileMicrosoftSignInIsOff() {
        assertDoesNotThrow(() -> check(false, null, null).check());
    }

    @Test
    void aCompleteSetupPasses() {
        assertDoesNotThrow(() -> check(true, "https://login.microsoftonline.com/t/v2.0", "client").check());
    }

    @Test
    void aMissingAuthorityUrlStopsTheStartWithAMessageThatNamesTheSetting() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> check(true, null, "client").check());
        assertTrue(e.getMessage().contains("QUARKUS_OIDC_AUTH_SERVER_URL"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> check(true, "  ", "client").check());
    }

    @Test
    void aMissingClientIdStopsTheStartToo() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> check(true, "https://login.microsoftonline.com/t/v2.0", null).check());
        assertTrue(e.getMessage().contains("QUARKUS_OIDC_CLIENT_ID"), e.getMessage());
    }
}
