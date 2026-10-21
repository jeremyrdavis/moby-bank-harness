package com.mobybank.harness.application;

/** A successful sign-in: the token for the browser's cookie and the user who signed in. */
public record LoginResult(String token, UserDTO user) {
}
