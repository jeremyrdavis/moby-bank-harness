package com.mobybank.harness.application;

/** Hashes passwords for storage and checks a password against a stored hash. */
public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String hash);
}
