package com.mobybank.harness.application;

/**
 * What a completed Microsoft sign-in tells us: the stable subject (the user's id at Microsoft), their email and name,
 * and the Graph tokens to act on their behalf.
 */
public record MicrosoftSignIn(String subject, String email, String displayName, MicrosoftTokens tokens) {
}
