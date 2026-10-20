package com.mobybank.harness.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Someone who can sign in: an analyst. An account can have a password (for the username/password sign-in), a Microsoft
 * identity (the subject of their Microsoft sign-in), or both. Identified by its {@link UserId}; the email is the
 * login name, kept in lower case.
 */
public final class AppUser {

    private final UserId id;
    private final String email;
    private final String displayName;
    private final String role;
    private final String passwordHash;
    private final String microsoftSubject;

    private AppUser(UserId id, String email, String displayName, String role, String passwordHash,
                    String microsoftSubject) {
        this.id = Objects.requireNonNull(id, "user id required");
        this.email = normalize(email);
        this.displayName = requireText(displayName, "display name");
        this.role = role == null ? "" : role.strip();
        this.passwordHash = passwordHash == null || passwordHash.isBlank() ? null : passwordHash;
        this.microsoftSubject = microsoftSubject == null || microsoftSubject.isBlank() ? null : microsoftSubject;
    }

    /** An account that signs in with an email and a password. {@code passwordHash} is already hashed. */
    public static AppUser withPassword(UserId id, String email, String displayName, String role, String passwordHash) {
        Objects.requireNonNull(passwordHash, "password hash required");
        return new AppUser(id, email, displayName, role, passwordHash, null);
    }

    /** An account created by a Microsoft sign-in. It has no password. */
    public static AppUser fromMicrosoft(UserId id, String email, String displayName, String role, String subject) {
        Objects.requireNonNull(subject, "microsoft subject required");
        return new AppUser(id, email, displayName, role, null, subject);
    }

    /** Reconstruction path for the persistence layer. Domain code never calls it. */
    public static AppUser rehydrate(UserId id, String email, String displayName, String role, String passwordHash,
                                    String microsoftSubject) {
        return new AppUser(id, email, displayName, role, passwordHash, microsoftSubject);
    }

    /** The same account, now also linked to a Microsoft identity. */
    public AppUser linkedToMicrosoft(String subject) {
        Objects.requireNonNull(subject, "microsoft subject required");
        return new AppUser(id, email, displayName, role, passwordHash, subject);
    }

    /** The email the way it is stored and compared: trimmed and in lower case. */
    public static String normalize(String email) {
        Objects.requireNonNull(email, "email required");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.indexOf('@') < 1 || normalized.indexOf('@') == normalized.length() - 1
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("a valid email is required");
        }
        return normalized;
    }

    private static String requireText(String value, String what) {
        Objects.requireNonNull(value, what + " required");
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return stripped;
    }

    public UserId id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String role() {
        return role;
    }

    /** The stored password hash, if the account can sign in with a password. */
    public Optional<String> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    /** The Microsoft subject, if the account has signed in with Microsoft. */
    public Optional<String> microsoftSubject() {
        return Optional.ofNullable(microsoftSubject);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AppUser user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
