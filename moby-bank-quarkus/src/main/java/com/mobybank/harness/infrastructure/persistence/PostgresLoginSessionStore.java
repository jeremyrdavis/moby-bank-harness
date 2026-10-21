package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.application.LoginSessionStore;
import com.mobybank.harness.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Keeps login sessions in PostgreSQL. The token is 256 random bits, handed out once; only its SHA-256 hash is stored,
 * so a copy of the database cannot be used to sign in. Expired sessions are removed when they are looked up.
 */
@ApplicationScoped
public class PostgresLoginSessionStore implements LoginSessionStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoginSessionJpaRepository jpa;
    private final Clock clock;

    @Inject
    public PostgresLoginSessionStore(LoginSessionJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String open(UserId user, Duration timeToLive) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LoginSessionEntity row = new LoginSessionEntity();
        row.tokenHash = hash(token);
        row.userId = user.value();
        row.createdAt = clock.instant();
        row.expiresAt = row.createdAt.plus(timeToLive);
        jpa.persist(row);
        return token;
    }

    @Override
    @Transactional
    public Optional<UserId> resolve(String token) {
        LoginSessionEntity row = jpa.findById(hash(token));
        if (row == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (!row.expiresAt.isAfter(now)) {
            jpa.delete(row);
            return Optional.empty();
        }
        return Optional.of(new UserId(row.userId));
    }

    @Override
    @Transactional
    public void close(String token) {
        jpa.deleteById(hash(token));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
