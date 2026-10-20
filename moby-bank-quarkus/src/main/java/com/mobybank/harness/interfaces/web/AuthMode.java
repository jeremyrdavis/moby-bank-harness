package com.mobybank.harness.interfaces.web;

/** {@code harness.auth.mode}: whether analysts must sign in. */
enum AuthMode {
    /** Everybody is the demo user. */
    NONE,
    /** Every page and API call needs a signed-in user. */
    LOGIN;

    static AuthMode parse(String value) {
        return switch (value == null ? "" : value.strip().toLowerCase()) {
            case "none" -> NONE;
            case "login" -> LOGIN;
            default -> throw new IllegalStateException(
                    "Unsupported harness.auth.mode=" + value + " (supported: login, none)");
        };
    }
}
