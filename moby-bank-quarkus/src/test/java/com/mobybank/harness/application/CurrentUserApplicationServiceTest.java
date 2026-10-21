package com.mobybank.harness.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mobybank.harness.application.Fakes.Caller;
import com.mobybank.harness.application.Fakes.PlainHasher;
import com.mobybank.harness.application.Fakes.Users;
import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.UserId;
import org.junit.jupiter.api.Test;

class CurrentUserApplicationServiceTest {

    private final Users users = new Users();
    private final Caller caller = new Caller();

    private CurrentUserApplicationService service(String fallbackName) {
        return new CurrentUserApplicationService(caller, users, fallbackName, "Credit Research");
    }

    @Test
    void describesTheStoredAccountOfWhoeverIsCalling() {
        users.persist(AppUser.withPassword(new UserId("u-1"), "rana.kapoor@mobybank.com", "Rana Kapoor", "Credit Research",
                new PlainHasher().hash("x")));
        caller.user = new UserId("u-1");

        UserDTO me = service("Hermione Granger").me();

        assertEquals("Rana Kapoor", me.name());
        assertEquals("Credit Research", me.role());
        assertEquals("RK", me.initials());
        assertEquals("u-1", me.id());
    }

    @Test
    void withNoStoredAccountTheConfiguredAnalystIsDescribed() {
        UserDTO me = service("Hermione Granger").me();

        assertEquals("Hermione Granger", me.name());
        assertEquals("Credit Research", me.role());
        assertEquals("HG", me.initials());
        assertEquals("demo-analyst", me.id());
    }

    @Test
    void initialsCoverSingleAndMultiPartNames() {
        assertEquals("P", service("plato").me().initials());
        assertEquals("AB", service("ada  byron lovelace").me().initials());
        assertEquals("?", service("   ").me().initials());
    }
}
