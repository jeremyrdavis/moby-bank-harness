package com.mobybank.harness.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A signed-in session. Only the SHA-256 hash of the token is stored, never the token the browser holds. */
@Entity
@Table(name = "login_session")
public class LoginSessionEntity {

    /** Hex SHA-256 of the session token. */
    @Id
    @Column(length = 64)
    public String tokenHash;

    @Column(nullable = false)
    public String userId;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant expiresAt;
}
