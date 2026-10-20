package com.mobybank.harness.application;

import jakarta.enterprise.context.RequestScoped;

/**
 * Holds who the current request is from. The authentication filter of the web layer sets it from the session cookie;
 * {@link RequestCurrentUser} reads it.
 */
@RequestScoped
public class RequestIdentity {

    private String userId;

    /** Records the id of the user the request came from. */
    public void signedInAs(String userId) {
        this.userId = userId;
    }

    String userId() {
        return userId;
    }
}
