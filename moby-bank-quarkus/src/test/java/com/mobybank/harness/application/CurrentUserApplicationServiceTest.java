package com.mobybank.harness.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CurrentUserApplicationServiceTest {

    @Test
    void describesTheConfiguredAnalyst() {
        UserDTO me = new CurrentUserApplicationService("Hermione Granger", "Credit Research").me();
        assertEquals("Hermione Granger", me.name());
        assertEquals("Credit Research", me.role());
        assertEquals("HG", me.initials());
        assertEquals("demo-analyst", me.id());
    }

    @Test
    void initialsCoverSingleAndMultiPartNames() {
        assertEquals("P", new CurrentUserApplicationService("plato", "x").me().initials());
        assertEquals("AB", new CurrentUserApplicationService("ada  byron lovelace", "x").me().initials());
        assertEquals("?", new CurrentUserApplicationService("   ", "x").me().initials());
    }
}
