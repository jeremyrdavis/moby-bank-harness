package com.mobybank.harness.application;

import java.time.Instant;

/** The Microsoft Graph tokens of a user who signed in with Microsoft. They never leave the server. */
public record MicrosoftTokens(String accessToken, String refreshToken, Instant expiresAt) {
}
