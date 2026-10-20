package com.mobybank.harness.application;

import com.mobybank.harness.domain.UserId;
import java.util.Optional;

/**
 * Remembers each user's Microsoft Graph tokens, so the OneDrive adapter can act as that user. The tokens are kept in
 * memory only: after a restart the user signs in with Microsoft again.
 */
public interface UserGraphTokens {

    void remember(UserId user, MicrosoftTokens tokens);

    Optional<MicrosoftTokens> find(UserId user);

    void forget(UserId user);
}
