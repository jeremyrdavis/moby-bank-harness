package com.mobybank.harness.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppUserTest {

    @Test
    void theEmailIsKeptInLowerCaseAndTrimmed() {
        AppUser user = AppUser.withPassword(new UserId("u"), "  Rana.Kapoor@MobyBank.com ", "Rana Kapoor", "Credit", "hash");

        assertEquals("rana.kapoor@mobybank.com", user.email());
        assertEquals(Optional.of("hash"), user.passwordHash());
        assertEquals(Optional.empty(), user.microsoftSubject());
    }

    @Test
    void anAccountNeedsAValidEmailAndAName() {
        assertThrows(IllegalArgumentException.class, () -> AppUser.withPassword(new UserId("u"), "not-an-email", "Name", "", "h"));
        assertThrows(IllegalArgumentException.class, () -> AppUser.withPassword(new UserId("u"), "a b@c.d", "Name", "", "h"));
        assertThrows(IllegalArgumentException.class, () -> AppUser.withPassword(new UserId("u"), "a@b.c", "  ", "", "h"));
    }

    @Test
    void aMicrosoftAccountHasNoPasswordAndCanBeLinkedLater() {
        AppUser fromMicrosoft = AppUser.fromMicrosoft(new UserId("m"), "a@b.com", "A B", "", "tenant:oid");
        assertTrue(fromMicrosoft.passwordHash().isEmpty());
        assertEquals(Optional.of("tenant:oid"), fromMicrosoft.microsoftSubject());

        AppUser withPassword = AppUser.withPassword(new UserId("p"), "a@b.com", "A B", "", "hash");
        AppUser linked = withPassword.linkedToMicrosoft("tenant:oid");
        assertEquals(new UserId("p"), linked.id());
        assertEquals(Optional.of("hash"), linked.passwordHash());
        assertEquals(Optional.of("tenant:oid"), linked.microsoftSubject());
    }

    @Test
    void accountsAreEqualWhenTheirIdsAre() {
        assertEquals(AppUser.withPassword(new UserId("u"), "a@b.com", "A", "", "h"),
                AppUser.withPassword(new UserId("u"), "z@b.com", "Z", "", "x"));
    }
}
