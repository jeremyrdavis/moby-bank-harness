package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.MicrosoftTokens;
import com.mobybank.harness.application.UserGraphTokens;
import com.mobybank.harness.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the users' Microsoft Graph tokens in memory, on purpose: refresh tokens are as good as a password for the
 * user's files, so they are never written to the database or sent to the browser. After a restart users who signed in
 * with Microsoft sign in again.
 */
@ApplicationScoped
public class InMemoryUserGraphTokens implements UserGraphTokens {

    private final Map<UserId, MicrosoftTokens> tokens = new ConcurrentHashMap<>();

    @Override
    public void remember(UserId user, MicrosoftTokens microsoftTokens) {
        tokens.put(user, microsoftTokens);
    }

    @Override
    public Optional<MicrosoftTokens> find(UserId user) {
        return Optional.ofNullable(tokens.get(user));
    }

    @Override
    public void forget(UserId user) {
        tokens.remove(user);
    }
}
