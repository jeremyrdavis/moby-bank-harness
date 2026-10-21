package com.mobybank.harness.application;

import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.domain.UserRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Signs analysts in and out. Two ways in, one result: a session token for the browser's cookie. A password sign-in
 * checks the stored hash; a Microsoft sign-in (completed elsewhere by the OpenID Connect flow) finds or creates the
 * account for the Microsoft identity and remembers the user's Graph tokens. Failed password attempts are counted per
 * email, and too many in a row lock that email out for a while.
 */
@ApplicationScoped
public class AuthenticationApplicationService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);
    private static final String GENERIC_FAILURE = "The email or password is not right.";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final LoginSessionStore loginSessions;
    private final UserGraphTokens graphTokens;
    private final Clock clock;
    private final Duration sessionTtl;
    private final boolean microsoftEnabled;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private volatile String dummyHash;

    @Inject
    public AuthenticationApplicationService(
            UserRepository users, PasswordHasher hasher, LoginSessionStore loginSessions, UserGraphTokens graphTokens,
            Clock clock,
            @ConfigProperty(name = "harness.auth.session-ttl", defaultValue = "8h") Duration sessionTtl,
            @ConfigProperty(name = "harness.auth.microsoft.enabled", defaultValue = "false") boolean microsoftEnabled) {
        this.users = users;
        this.hasher = hasher;
        this.loginSessions = loginSessions;
        this.graphTokens = graphTokens;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
        this.microsoftEnabled = microsoftEnabled;
    }

    /** What the sign-in page should offer. */
    public AuthOptionsDTO options() {
        return new AuthOptionsDTO(microsoftEnabled, true);
    }

    /**
     * Signs in with an email and a password.
     *
     * @throws InvalidCredentialsException if they are wrong, or the email is locked out; the message never says which
     *                                     part was wrong or whether the account exists
     */
    public LoginResult loginWithPassword(String email, String password) {
        String key = email == null ? "" : email.strip().toLowerCase();
        if (isLockedOut(key)) {
            Log.warnf("Sign-in refused: too many failed attempts for one email");
            throw new InvalidCredentialsException("Too many sign-in attempts. Try again in a few minutes.", true);
        }
        Optional<AppUser> user = Optional.empty();
        if (email != null && password != null && !password.isEmpty()) {
            try {
                user = users.findByEmail(email);
            } catch (IllegalArgumentException e) {
                user = Optional.empty(); // not a valid email
            }
        }
        boolean ok = user.flatMap(AppUser::passwordHash).map(hash -> hasher.matches(password, hash)).orElseGet(() -> {
            // spend the same time as a real check, so timing does not tell whether the account exists
            hasher.matches(password == null ? "" : password, dummyHash());
            return false;
        });
        if (!ok) {
            recordFailure(key);
            Log.debugf("Password sign-in failed");
            throw new InvalidCredentialsException(GENERIC_FAILURE);
        }
        failures.remove(key);
        return open(user.get());
    }

    /** Signs in a user whose Microsoft sign-in just completed, creating their account the first time. */
    public LoginResult signInWithMicrosoft(MicrosoftSignIn signIn) {
        AppUser user = users.findByMicrosoftSubject(signIn.subject()).orElseGet(() -> linkOrCreate(signIn));
        graphTokens.remember(user.id(), signIn.tokens());
        Log.debugf("Microsoft sign-in for user %s", user.id().value());
        return open(user);
    }

    /** The user a session token belongs to, if it is still valid. */
    public Optional<UserDTO> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return loginSessions.resolve(token).flatMap(users::findById).map(AuthenticationApplicationService::toDto);
    }

    /** Ends a session and forgets the user's Microsoft tokens. */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        loginSessions.resolve(token).ifPresent(graphTokens::forget);
        loginSessions.close(token);
    }

    /** A hash to compare against when the account does not exist, so the check takes as long as a real one. */
    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = hasher.hash("no-such-account");
            dummyHash = hash;
        }
        return hash;
    }

    private AppUser linkOrCreate(MicrosoftSignIn signIn) {
        Optional<AppUser> sameEmail = signIn.email() == null ? Optional.empty() : findByEmailQuietly(signIn.email());
        if (sameEmail.isPresent()) {
            AppUser linked = sameEmail.get().linkedToMicrosoft(signIn.subject());
            users.persist(linked);
            return linked;
        }
        String email = signIn.email() == null || signIn.email().isBlank()
                ? signIn.subject() + "@microsoft.invalid" : signIn.email();
        String name = signIn.displayName() == null || signIn.displayName().isBlank() ? email : signIn.displayName();
        AppUser created = AppUser.fromMicrosoft(new UserId("user-" + UUID.randomUUID()), email, name, "", signIn.subject());
        users.persist(created);
        return created;
    }

    private Optional<AppUser> findByEmailQuietly(String email) {
        try {
            return users.findByEmail(email);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private LoginResult open(AppUser user) {
        String token = loginSessions.open(user.id(), sessionTtl);
        Log.debugf("User %s signed in", user.id().value());
        return new LoginResult(token, toDto(user));
    }

    static UserDTO toDto(AppUser user) {
        return new UserDTO(user.id().value(), user.displayName(), user.role(), CurrentUserApplicationService.initials(user.displayName()));
    }

    // --- failed attempts -----------------------------------------------------------------------------------------

    private boolean isLockedOut(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            prune(recent);
            return recent.size() >= MAX_FAILURES;
        }
    }

    private void recordFailure(String key) {
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(clock.instant());
        }
    }

    private void prune(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(LOCKOUT_WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.removeFirst();
        }
    }
}
