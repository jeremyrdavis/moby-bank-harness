package com.mobybank.harness.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RecencyGroupTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void sameCalendarDayIsToday() {
        assertEquals(RecencyGroup.TODAY, RecencyGroup.of(Instant.parse("2026-09-30T00:00:01Z"), NOW, UTC));
        assertEquals(RecencyGroup.TODAY, RecencyGroup.of(NOW, NOW, UTC));
    }

    @Test
    void futureTimestampsCountAsToday() {
        assertEquals(RecencyGroup.TODAY, RecencyGroup.of(NOW.plusSeconds(3600), NOW, UTC));
    }

    @Test
    void yesterdayThroughSevenDaysAgoIsPrevious7Days() {
        assertEquals(RecencyGroup.PREVIOUS_7_DAYS, RecencyGroup.of(Instant.parse("2026-09-29T23:59:00Z"), NOW, UTC));
        assertEquals(RecencyGroup.PREVIOUS_7_DAYS, RecencyGroup.of(Instant.parse("2026-09-23T00:00:00Z"), NOW, UTC));
    }

    @Test
    void olderThanSevenDaysIsEarlier() {
        assertEquals(RecencyGroup.EARLIER, RecencyGroup.of(Instant.parse("2026-09-22T23:59:00Z"), NOW, UTC));
    }

    @Test
    void labelsMatchTheUi() {
        assertEquals("Today", RecencyGroup.TODAY.label());
        assertEquals("Previous 7 days", RecencyGroup.PREVIOUS_7_DAYS.label());
        assertEquals("Earlier", RecencyGroup.EARLIER.label());
    }

    @Test
    void zoneDecidesWhereMidnightFalls() {
        Instant lateUtc = Instant.parse("2026-09-29T23:30:00Z");
        ZoneId tokyo = ZoneId.of("Asia/Tokyo"); // already the 30th there
        assertEquals(RecencyGroup.TODAY, RecencyGroup.of(lateUtc, NOW, tokyo));
        assertEquals(RecencyGroup.PREVIOUS_7_DAYS, RecencyGroup.of(lateUtc, NOW, UTC));
    }
}
