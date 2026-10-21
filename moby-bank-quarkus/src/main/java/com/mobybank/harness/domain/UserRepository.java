package com.mobybank.harness.domain;

import java.util.Optional;

/** Stores the {@link AppUser}s who can sign in. */
public interface UserRepository {

    Optional<AppUser> findById(UserId id);

    /** The account with this email (compared in lower case), if any. */
    Optional<AppUser> findByEmail(String email);

    /** The account linked to this Microsoft subject, if any. */
    Optional<AppUser> findByMicrosoftSubject(String subject);

    /** Creates the account, or replaces the stored one with the same id. */
    void persist(AppUser user);

    /** True when no account exists yet. */
    boolean isEmpty();
}
