package com.mobybank.harness.infrastructure.graph;

import com.mobybank.harness.domain.UserId;

/** Where {@link GraphDocumentCatalog} gets the bearer token for a request made on behalf of a user. */
public interface GraphTokenSource {

    /**
     * The token to send to Microsoft Graph for this user.
     *
     * @throws com.mobybank.harness.domain.SignInRequiredException if the user has none and must sign in with Microsoft
     * @throws com.mobybank.harness.domain.DocumentSourceException if a token cannot be obtained
     */
    String tokenFor(UserId user);

    /** The same token, from {@code provider}, for every user (the app-wide credential). */
    static GraphTokenSource appWide(GraphTokenProvider provider) {
        return user -> provider.token();
    }
}
