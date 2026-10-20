package com.mobybank.harness.infrastructure.graph;

import com.mobybank.harness.application.MicrosoftTokens;
import com.mobybank.harness.application.UserGraphTokens;
import com.mobybank.harness.domain.SignInRequiredException;
import com.mobybank.harness.domain.UserId;
import io.quarkus.logging.Log;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Gives each user their own Graph token, so a user only ever reaches what they can open in OneDrive. A user who signed
 * in with Microsoft has an access token, renewed with their refresh token when it is about to expire. A user without one
 * (they signed in with a password) falls back to the app-wide credential when one is configured, and otherwise has to
 * sign in with Microsoft.
 */
public final class UserGraphTokenSource implements GraphTokenSource {

    /** Renew this long before the token expires, so a request never starts with a token that is about to lapse. */
    static final Duration EARLY_REFRESH = Duration.ofSeconds(60);

    private final UserGraphTokens userTokens;
    private final GraphTokenProvider appWide;
    private final MicrosoftTokenRefresher refresher;
    private final Clock clock;

    /** @param appWide the app-wide credential, or null when there is none */
    public UserGraphTokenSource(UserGraphTokens userTokens, GraphTokenProvider appWide,
                                MicrosoftTokenRefresher refresher, Clock clock) {
        this.userTokens = userTokens;
        this.appWide = appWide;
        this.refresher = refresher;
        this.clock = clock;
    }

    @Override
    public String tokenFor(UserId user) {
        Optional<MicrosoftTokens> own = userTokens.find(user);
        if (own.isPresent()) {
            return usable(user, own.get());
        }
        if (appWide != null) {
            Log.tracef("User %s has no Microsoft sign-in; using the app-wide credential", user.value());
            return appWide.token();
        }
        throw new SignInRequiredException("Sign in with Microsoft to use your OneDrive.");
    }

    private String usable(UserId user, MicrosoftTokens tokens) {
        if (tokens.expiresAt().minus(EARLY_REFRESH).isAfter(clock.instant())) {
            return tokens.accessToken();
        }
        Log.debugf("Renewing the Microsoft access token of user %s", user.value());
        try {
            MicrosoftTokens renewed = refresher.refresh(tokens);
            userTokens.remember(user, renewed);
            return renewed.accessToken();
        } catch (SignInRequiredException e) {
            userTokens.forget(user);
            throw e;
        }
    }
}
