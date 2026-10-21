package com.mobybank.harness.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.Fakes.GraphTokens;
import com.mobybank.harness.application.Fakes.LoginSessions;
import com.mobybank.harness.application.Fakes.PlainHasher;
import com.mobybank.harness.application.Fakes.Users;
import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationApplicationServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-19T12:00:00Z");

    private Users users;
    private LoginSessions sessions;
    private GraphTokens graphTokens;
    private MutableClock clock;
    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        users = new Users();
        sessions = new LoginSessions();
        graphTokens = new GraphTokens();
        clock = new MutableClock(T0);
        service = new AuthenticationApplicationService(users, new PlainHasher(), sessions, graphTokens, clock,
                Duration.ofHours(8), true);
        users.persist(AppUser.withPassword(new UserId("u-rana"), "rana.kapoor@mobybank.com", "Rana Kapoor",
                "Credit Research", "hash:right"));
    }

    // --- passwords ---------------------------------------------------------------------------------------------

    @Test
    void theRightPasswordSignsTheUserIn() {
        LoginResult result = service.loginWithPassword("Rana.Kapoor@mobybank.com ", "right");

        assertEquals("Rana Kapoor", result.user().name());
        assertEquals("RK", result.user().initials());
        assertEquals(new UserId("u-rana"), sessions.resolve(result.token()).orElseThrow());
        assertEquals("u-rana", service.resolve(result.token()).orElseThrow().id());
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() {
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> service.loginWithPassword("rana.kapoor@mobybank.com", "wrong"));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> service.loginWithPassword("nobody@mobybank.com", "right"));
        InvalidCredentialsException notAnEmail = assertThrows(InvalidCredentialsException.class,
                () -> service.loginWithPassword("not an email", "right"));

        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertEquals(wrong.getMessage(), notAnEmail.getMessage());
        assertFalse(wrong.lockedOut());
        assertTrue(sessions.open.isEmpty(), "no session was started");
    }

    @Test
    void emptyInputIsRejected() {
        assertThrows(InvalidCredentialsException.class, () -> service.loginWithPassword(null, null));
        assertThrows(InvalidCredentialsException.class, () -> service.loginWithPassword("rana.kapoor@mobybank.com", ""));
    }

    @Test
    void anAccountWithoutAPasswordCannotSignInWithOne() {
        users.persist(AppUser.fromMicrosoft(new UserId("u-ms"), "ms@mobybank.com", "Ms User", "", "t:o"));

        assertThrows(InvalidCredentialsException.class, () -> service.loginWithPassword("ms@mobybank.com", "anything"));
    }

    @Test
    void fiveFailuresLockTheEmailOutEvenForTheRightPasswordUntilTheyAgeOut() {
        for (int i = 0; i < AuthenticationApplicationService.MAX_FAILURES; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.loginWithPassword("rana.kapoor@mobybank.com", "wrong"));
        }

        InvalidCredentialsException locked = assertThrows(InvalidCredentialsException.class,
                () -> service.loginWithPassword("rana.kapoor@mobybank.com", "right"));
        assertTrue(locked.lockedOut());

        clock.advance(AuthenticationApplicationService.LOCKOUT_WINDOW.plusSeconds(1));
        assertEquals("Rana Kapoor", service.loginWithPassword("rana.kapoor@mobybank.com", "right").user().name());
    }

    @Test
    void aSuccessfulSignInClearsTheFailureCount() {
        for (int i = 0; i < AuthenticationApplicationService.MAX_FAILURES - 1; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.loginWithPassword("rana.kapoor@mobybank.com", "wrong"));
        }
        service.loginWithPassword("rana.kapoor@mobybank.com", "right");

        for (int i = 0; i < AuthenticationApplicationService.MAX_FAILURES - 1; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.loginWithPassword("rana.kapoor@mobybank.com", "wrong"));
        }
        assertEquals("Rana Kapoor", service.loginWithPassword("rana.kapoor@mobybank.com", "right").user().name());
    }

    // --- Microsoft ---------------------------------------------------------------------------------------------

    private static MicrosoftSignIn microsoft(String subject, String email, String name) {
        return new MicrosoftSignIn(subject, email, name,
                new MicrosoftTokens("access-1", "refresh-1", T0.plusSeconds(3600)));
    }

    @Test
    void theFirstMicrosoftSignInCreatesTheAccountAndRemembersTheGraphTokens() {
        LoginResult result = service.signInWithMicrosoft(microsoft("t:oid-1", "Ada@Example.com", "Ada Lovelace"));

        UserId id = new UserId(result.user().id());
        AppUser created = users.findById(id).orElseThrow();
        assertEquals("ada@example.com", created.email());
        assertEquals("Ada Lovelace", created.displayName());
        assertEquals("t:oid-1", created.microsoftSubject().orElseThrow());
        assertTrue(created.passwordHash().isEmpty());
        assertEquals("access-1", graphTokens.find(id).orElseThrow().accessToken());
        assertEquals(id, sessions.resolve(result.token()).orElseThrow());
    }

    @Test
    void theNextMicrosoftSignInFindsTheSameAccount() {
        UserId first = new UserId(service.signInWithMicrosoft(microsoft("t:oid-1", "ada@example.com", "Ada")).user().id());
        UserId second = new UserId(service.signInWithMicrosoft(microsoft("t:oid-1", "ada@example.com", "Ada")).user().id());

        assertEquals(first, second);
    }

    @Test
    void aMicrosoftSignInWithTheEmailOfAnExistingAccountLinksIt() {
        LoginResult result = service.signInWithMicrosoft(
                microsoft("t:oid-9", "rana.kapoor@mobybank.com", "Rana K."));

        assertEquals("u-rana", result.user().id());
        AppUser linked = users.findById(new UserId("u-rana")).orElseThrow();
        assertEquals("t:oid-9", linked.microsoftSubject().orElseThrow());
        assertEquals("hash:right", linked.passwordHash().orElseThrow(), "the password still works");
    }

    @Test
    void aMicrosoftSignInWithoutAnEmailStillGetsAnAccount() {
        LoginResult result = service.signInWithMicrosoft(microsoft("t:oid-2", null, null));

        assertTrue(users.findById(new UserId(result.user().id())).isPresent());
    }

    // --- sessions ----------------------------------------------------------------------------------------------

    @Test
    void signingOutEndsTheSessionAndForgetsTheMicrosoftTokens() {
        LoginResult result = service.signInWithMicrosoft(microsoft("t:oid-1", "ada@example.com", "Ada"));
        UserId id = new UserId(result.user().id());

        service.logout(result.token());

        assertTrue(service.resolve(result.token()).isEmpty());
        assertTrue(graphTokens.find(id).isEmpty());
    }

    @Test
    void unknownOrBlankTokensResolveToNobodyAndLogoutIgnoresThem() {
        assertTrue(service.resolve(null).isEmpty());
        assertTrue(service.resolve(" ").isEmpty());
        assertTrue(service.resolve("nope").isEmpty());
        service.logout(null);
        service.logout("nope");
    }

    @Test
    void thePageOffersMicrosoftOnlyWhenItIsSetUp() {
        assertTrue(service.options().microsoft());
        assertTrue(service.options().password());
        AuthenticationApplicationService off = new AuthenticationApplicationService(users, new PlainHasher(), sessions,
                graphTokens, clock, Duration.ofHours(8), false);
        assertFalse(off.options().microsoft());
    }

    /** A clock the test moves. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
