package com.mobybank.harness.infrastructure.persistence;

import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.domain.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;

/** Stores the {@link AppUser}s in PostgreSQL. */
@ApplicationScoped
public class PostgresUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    @Inject
    public PostgresUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Optional<AppUser> findById(UserId id) {
        return Optional.ofNullable(jpa.findById(id.value())).map(PostgresUserRepository::toAggregate);
    }

    @Override
    @Transactional
    public Optional<AppUser> findByEmail(String email) {
        String normalized = AppUser.normalize(email);
        return jpa.find("email", normalized).firstResultOptional().map(PostgresUserRepository::toAggregate);
    }

    @Override
    @Transactional
    public Optional<AppUser> findByMicrosoftSubject(String subject) {
        return jpa.find("microsoftSubject", subject).firstResultOptional().map(PostgresUserRepository::toAggregate);
    }

    @Override
    @Transactional
    public void persist(AppUser user) {
        UserEntity row = jpa.findById(user.id().value());
        boolean isNew = row == null;
        if (isNew) {
            row = new UserEntity();
            row.id = user.id().value();
        }
        row.email = user.email();
        row.displayName = user.displayName();
        row.role = user.role();
        row.passwordHash = user.passwordHash().orElse(null);
        row.microsoftSubject = user.microsoftSubject().orElse(null);
        if (isNew) {
            jpa.persist(row);
        }
        jpa.flush();
    }

    @Override
    @Transactional
    public boolean isEmpty() {
        return jpa.count() == 0;
    }

    private static AppUser toAggregate(UserEntity row) {
        return AppUser.rehydrate(new UserId(row.id), row.email, row.displayName, row.role, row.passwordHash,
                row.microsoftSubject);
    }
}
