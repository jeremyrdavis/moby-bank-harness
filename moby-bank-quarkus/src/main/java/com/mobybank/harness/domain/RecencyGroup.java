package com.mobybank.harness.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** How recently a session was active, used to group the conversation history. */
public enum RecencyGroup {
    TODAY("Today"),
    PREVIOUS_7_DAYS("Previous 7 days"),
    EARLIER("Earlier");

    private final String label;

    RecencyGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RecencyGroup of(Instant updatedAt, Instant now, ZoneId zone) {
        LocalDate updated = updatedAt.atZone(zone).toLocalDate();
        LocalDate today = now.atZone(zone).toLocalDate();
        if (!updated.isBefore(today)) {
            return TODAY;
        }
        if (!updated.isBefore(today.minusDays(7))) {
            return PREVIOUS_7_DAYS;
        }
        return EARLIER;
    }
}
