package com.mobybank.harness.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The stored form of an {@code AppUser}. The email is unique and kept in lower case. */
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    public String id;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(nullable = false)
    public String displayName;

    @Column(nullable = false)
    public String role = "";

    /** A bcrypt hash; null for accounts that only sign in with Microsoft. */
    @Column(columnDefinition = "text")
    public String passwordHash;

    @Column(unique = true)
    public String microsoftSubject;
}
