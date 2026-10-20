package com.mobybank.harness.domain;

/**
 * The action needs the user's own Microsoft access (for example to read their OneDrive) and they have not signed in
 * with Microsoft, or their access expired. The REST layer maps this to 401.
 */
public class SignInRequiredException extends DomainException {

    public SignInRequiredException(String message) {
        super(message);
    }
}
