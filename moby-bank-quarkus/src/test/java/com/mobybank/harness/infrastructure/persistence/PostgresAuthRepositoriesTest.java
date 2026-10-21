package com.mobybank.harness.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.LoginSessionStore;
import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.domain.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Users, login sessions and session owners against a real PostgreSQL (Dev Services starts it). */
@QuarkusTest
class PostgresAuthRepositoriesTest {

    private static final Instant T0 = Instant.parse("2026-10-19T12:00:00Z");

    @Inject
    UserRepository users;

    @Inject
    LoginSessionStore loginSessions;

    @Inject
    LoginSessionJpaRepository loginSessionRows;

    @Inject
    SessionRepository sessions;

    @Inject
    SessionJpaRepository sessionRows;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    // --- users -------------------------------------------------------------------------------------------------

    @Test
    void anAccountRoundTripsAndIsFoundByIdEmailAndMicrosoftSubject() {
        String name = unique("ada");
        AppUser ada = AppUser.fromMicrosoft(new UserId(name), name + "@Example.com", "Ada Lovelace", "Analyst", "t:" + name);
        users.persist(ada);

        AppUser byId = users.findById(ada.id()).orElseThrow();
        assertEquals(name + "@example.com", byId.email());
        assertEquals("Ada Lovelace", byId.displayName());
        assertEquals("Analyst", byId.role());
        assertEquals(Optional.of("t:" + name), byId.microsoftSubject());
        assertTrue(byId.passwordHash().isEmpty());
        assertEquals(ada.id(), users.findByEmail("  " + name.toUpperCase() + "@EXAMPLE.COM ").orElseThrow().id());
        assertEquals(ada.id(), users.findByMicrosoftSubject("t:" + name).orElseThrow().id());
        assertTrue(users.findById(new UserId(unique("nobody"))).isEmpty());
        assertTrue(users.findByEmail(unique("nobody") + "@example.com").isEmpty());
        assertTrue(users.findByMicrosoftSubject(unique("nobody")).isEmpty());
        assertFalse(users.isEmpty());
    }

    @Test
    void persistingAnExistingAccountUpdatesIt() {
        String name = unique("grace");
        users.persist(AppUser.withPassword(new UserId(name), name + "@example.com", "Grace", "", "hash-1"));

        users.persist(users.findById(new UserId(name)).orElseThrow().linkedToMicrosoft("t:" + name));

        AppUser updated = users.findById(new UserId(name)).orElseThrow();
        assertEquals(Optional.of("hash-1"), updated.passwordHash(), "the password is kept");
        assertEquals(Optional.of("t:" + name), updated.microsoftSubject());
    }

    @Test
    void twoAccountsCannotShareAnEmail() {
        String email = unique("shared") + "@example.com";
        users.persist(AppUser.withPassword(new UserId(unique("a")), email, "A", "", "h"));

        assertThrows(RuntimeException.class,
                () -> users.persist(AppUser.withPassword(new UserId(unique("b")), email, "B", "", "h")));
    }

    @Test
    void theSeededDemoAnalystsExistAndCanBeUsedToSignIn() {
        AppUser hermione = users.findByEmail("hermione.granger@mobybank.com").orElseThrow();
        assertEquals(UserId.DEMO, hermione.id(), "Hermione owns the demo data");
        assertTrue(hermione.passwordHash().orElseThrow().startsWith("$2"), "stored as a bcrypt hash");
        assertNotEquals("moby-demo", hermione.passwordHash().orElseThrow());
        assertEquals("Rana Kapoor", users.findByEmail("rana.kapoor@mobybank.com").orElseThrow().displayName());
    }

    // --- login sessions ----------------------------------------------------------------------------------------

    @Test
    void aTokenResolvesToItsUserUntilItIsClosed() {
        UserId user = new UserId(unique("u"));

        String token = loginSessions.open(user, Duration.ofHours(1));

        assertEquals(Optional.of(user), loginSessions.resolve(token));
        loginSessions.close(token);
        assertTrue(loginSessions.resolve(token).isEmpty());
        loginSessions.close(token); // closing twice is fine
    }

    @Test
    void everyTokenIsDifferentAndUnknownTokensResolveToNobody() {
        UserId user = new UserId(unique("u"));

        String first = loginSessions.open(user, Duration.ofHours(1));
        String second = loginSessions.open(user, Duration.ofHours(1));

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43, "256 random bits, base64: " + first);
        assertTrue(loginSessions.resolve("made-up").isEmpty());
    }

    @Test
    void onlyAHashOfTheTokenIsStored() {
        String token = loginSessions.open(new UserId(unique("u")), Duration.ofHours(1));

        assertNull(loginSessionRows.findById(token), "the token itself is not a key anywhere");
        assertEquals(64, PostgresLoginSessionStore.hash(token).length());
        assertTrue(loginSessionRows.findById(PostgresLoginSessionStore.hash(token)) != null);
    }

    @Test
    void anExpiredSessionResolvesToNobodyAndIsRemoved() {
        String token = loginSessions.open(new UserId(unique("u")), Duration.ZERO);

        assertTrue(loginSessions.resolve(token).isEmpty());
        assertNull(loginSessionRows.findById(PostgresLoginSessionStore.hash(token)));
    }

    // --- who owns a session ------------------------------------------------------------------------------------

    @Test
    void aSessionKeepsItsOwnerAndIsListedForThatOwnerOnly() {
        UserId rana = new UserId(unique("rana"));
        UserId other = new UserId(unique("other"));
        Session mine = Session.start(SessionId.fresh(), rana, Location.LOCAL, T0);
        Session theirs = Session.start(SessionId.fresh(), other, Location.LOCAL, T0.plusSeconds(1));
        sessions.persist(mine);
        sessions.persist(theirs);

        assertEquals(rana, sessions.findById(mine.id()).orElseThrow().owner());
        List<SessionId> ranas = sessions.findAllByOwner(rana).stream().map(Session::id).toList();
        assertEquals(List.of(mine.id()), ranas);
        assertEquals(List.of(theirs.id()), sessions.findAllByOwner(other).stream().map(Session::id).toList());
        assertTrue(sessions.findAllByOwner(new UserId(unique("nobody"))).isEmpty());
    }

    @Test
    void aSessionFromBeforeThereWereUsersBelongsToTheDemoUser() {
        SessionId id = SessionId.fresh();
        insertWithoutOwner(id);

        assertEquals(UserId.DEMO, sessions.findById(id).orElseThrow().owner());
        assertTrue(sessions.findAllByOwner(UserId.DEMO).stream().anyMatch(s -> s.id().equals(id)));
        assertFalse(sessions.findAllByOwner(new UserId(unique("rana"))).stream().anyMatch(s -> s.id().equals(id)));
    }

    @Transactional
    void insertWithoutOwner(SessionId id) {
        SessionEntity row = new SessionEntity();
        row.id = id.value();
        row.ownerId = null;
        row.title = "From before there were users";
        row.location = Location.LOCAL;
        row.status = com.mobybank.harness.domain.SessionStatus.IDLE;
        row.createdAt = T0;
        row.updatedAt = T0;
        row.version = 1;
        sessionRows.persist(row);
    }
}
