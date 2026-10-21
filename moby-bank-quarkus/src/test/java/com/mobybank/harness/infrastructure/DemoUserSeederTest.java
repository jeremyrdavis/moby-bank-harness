package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.PasswordHasher;
import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.domain.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DemoUserSeederTest {

    private static final class Users implements UserRepository {

        final Map<UserId, AppUser> stored = new HashMap<>();

        @Override
        public Optional<AppUser> findById(UserId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return stored.values().stream().filter(u -> u.email().equals(AppUser.normalize(email))).findFirst();
        }

        @Override
        public Optional<AppUser> findByMicrosoftSubject(String subject) {
            return Optional.empty();
        }

        @Override
        public void persist(AppUser user) {
            stored.put(user.id(), user);
        }

        @Override
        public boolean isEmpty() {
            return stored.isEmpty();
        }
    }

    private static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public String hash(String password) {
            return "hash:" + password;
        }

        @Override
        public boolean matches(String password, String hash) {
            return hash.equals("hash:" + password);
        }
    };

    @Test
    void createsHermioneAsTheDemoUserRanaAndTheDeveloperWithTheirPasswords() {
        Users users = new Users();

        new DemoUserSeeder(users, HASHER, true, "s3cret", "dev-s3cret").seed();

        AppUser hermione = users.findByEmail("hermione.granger@mobybank.com").orElseThrow();
        assertEquals(UserId.DEMO, hermione.id());
        assertEquals("Hermione Granger", hermione.displayName());
        assertEquals("hash:s3cret", hermione.passwordHash().orElseThrow());
        AppUser rana = users.findByEmail("rana.kapoor@mobybank.com").orElseThrow();
        assertEquals("Rana Kapoor", rana.displayName());
        assertEquals("hash:s3cret", rana.passwordHash().orElseThrow());
        AppUser jeremy = users.findByEmail("jeremy.davis@docker.com").orElseThrow();
        assertEquals("Jeremy Davis", jeremy.displayName());
        assertEquals("hash:dev-s3cret", jeremy.passwordHash().orElseThrow());
        assertEquals(3, users.stored.size());
    }

    @Test
    void addsOnlyTheMissingUsersToADatabaseThatAlreadyHasSome() {
        Users users = new Users();
        users.persist(AppUser.withPassword(UserId.DEMO, "hermione.granger@mobybank.com", "Hermione Granger",
                "Credit Research", "hash:changed-by-hand"));

        new DemoUserSeeder(users, HASHER, true, "s3cret", "dev-s3cret").seed();

        assertEquals(3, users.stored.size());
        assertEquals("hash:changed-by-hand", users.findByEmail("hermione.granger@mobybank.com").orElseThrow()
                .passwordHash().orElseThrow(), "an existing account is left alone");
        assertTrue(users.findByEmail("jeremy.davis@docker.com").isPresent());
    }

    @Test
    void seedingTwiceChangesNothing() {
        Users users = new Users();
        DemoUserSeeder seeder = new DemoUserSeeder(users, HASHER, true, "s3cret", "dev-s3cret");

        seeder.seed();
        seeder.seed();

        assertEquals(3, users.stored.size());
    }
}
