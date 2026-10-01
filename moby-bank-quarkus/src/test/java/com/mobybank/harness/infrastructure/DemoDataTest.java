package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.RecencyGroup;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionStatus;
import com.mobybank.harness.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoDataTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZONE);
    }

    @Test
    void theSeededHistoryMatchesThePrototype() {
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        InMemoryConnectedFoldersRepository folders = new InMemoryConnectedFoldersRepository();
        new DemoDataSeeder(sessions, folders, clockAt("2026-09-30T12:00:00Z"), true).seed();

        List<Session> history = sessions.findAllByMostRecentlyUpdated();
        assertEquals(List.of(
                "Fathom Industrial — Q2 2026 earnings",
                "Kestrel Foods — earnings release review",
                "Harborline Logistics — cash flow vs covenants",
                "Regional banks — NIM trend comparison",
                "Segment revenue bridge FY25 → FY26"), history.stream().map(s -> s.title().value()).toList());
        assertEquals(List.of(Location.LOCAL, Location.CLOUD, Location.LOCAL, Location.CLOUD, Location.LOCAL),
                history.stream().map(Session::location).toList());
        assertTrue(history.stream().allMatch(s -> s.status() == SessionStatus.IDLE));
        assertEquals(4, history.get(0).messages().size());
        assertEquals(5, history.get(0).messages().get(1).steps().size());
        assertEquals(4, history.get(0).messages().get(1).table().orElseThrow().rows().size());
        assertEquals(3, history.get(0).messages().get(0).files().size());
    }

    @Test
    void theSeededSessionsFallIntoTheThreeHistoryGroups() {
        for (String now : List.of("2026-09-30T12:00:00Z", "2026-09-30T00:10:00Z", "2026-09-30T23:55:00Z")) {
            Clock clock = clockAt(now);
            InMemorySessionRepository sessions = new InMemorySessionRepository();
            new DemoDataSeeder(sessions, new InMemoryConnectedFoldersRepository(), clock, true).seed();

            List<RecencyGroup> groups = sessions.findAllByMostRecentlyUpdated().stream()
                    .map(s -> RecencyGroup.of(s.updatedAt(), clock.instant(), ZONE))
                    .toList();

            assertEquals(List.of(RecencyGroup.TODAY, RecencyGroup.TODAY, RecencyGroup.PREVIOUS_7_DAYS,
                    RecencyGroup.PREVIOUS_7_DAYS, RecencyGroup.EARLIER), groups, "at " + now);
        }
    }

    @Test
    void messagesAreDatedWithTheirSession() {
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        new DemoDataSeeder(sessions, new InMemoryConnectedFoldersRepository(), clockAt("2026-09-30T12:00:00Z"), true)
                .seed();
        Session first = sessions.findAllByMostRecentlyUpdated().get(0);
        assertTrue(first.messages().stream().allMatch(m -> m.createdAt().equals(first.updatedAt())));
    }

    @Test
    void theFirstThreeFoldersStartConnected() {
        InMemoryConnectedFoldersRepository folders = new InMemoryConnectedFoldersRepository();
        new DemoDataSeeder(new InMemorySessionRepository(), folders, clockAt("2026-09-30T12:00:00Z"), true).seed();

        ConnectedFolders connected = folders.findByUser(UserId.DEMO).orElseThrow();
        assertEquals(List.of(new FolderId("f1"), new FolderId("f2"), new FolderId("f3")), connected.folderIds());
    }

    @Test
    void idsAreTheSameOnEveryStart() {
        InMemorySessionRepository one = new InMemorySessionRepository();
        InMemorySessionRepository two = new InMemorySessionRepository();
        new DemoDataSeeder(one, new InMemoryConnectedFoldersRepository(), clockAt("2026-09-30T12:00:00Z"), true).seed();
        new DemoDataSeeder(two, new InMemoryConnectedFoldersRepository(), clockAt("2026-10-05T08:00:00Z"), true).seed();

        assertEquals(one.findAllByMostRecentlyUpdated().stream().map(Session::id).toList(),
                two.findAllByMostRecentlyUpdated().stream().map(Session::id).toList());
    }
}
