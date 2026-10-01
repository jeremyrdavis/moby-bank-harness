package com.mobybank.harness.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionStatus;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryRepositoriesTest {

    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");

    // --- sessions ----------------------------------------------------------------------------------------------

    @Test
    void aNewSessionCanBeSavedAndLoaded() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, T0);
        repo.persist(session);

        Session loaded = repo.findById(session.id()).orElseThrow();
        assertEquals(session.id(), loaded.id());
        assertEquals(1L, loaded.version(), "the first save advances the version from 0 to 1");
        assertTrue(repo.findById(SessionId.fresh()).isEmpty());
    }

    @Test
    void callersNeverShareAMutableSession() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, T0);
        repo.persist(session);

        Session first = repo.findById(session.id()).orElseThrow();
        Session second = repo.findById(session.id()).orElseThrow();
        assertNotSame(first, second);

        first.beginMove(Location.CLOUD, T0.plusSeconds(1)); // not saved
        assertEquals(SessionStatus.IDLE, repo.findById(session.id()).orElseThrow().status());
    }

    @Test
    void savingAdvancesTheVersionEachTime() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, T0);
        repo.persist(session);

        Session loaded = repo.findById(session.id()).orElseThrow();
        loaded.postUserMessage("hello", List.of(), T0.plusSeconds(1));
        repo.persist(loaded);

        Session reloaded = repo.findById(session.id()).orElseThrow();
        assertEquals(2L, reloaded.version());
        assertEquals(1, reloaded.messages().size());
        assertEquals(SessionStatus.RUNNING, reloaded.status());
    }

    @Test
    void savingAStaleCopyIsRejected() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, T0);
        repo.persist(session);

        Session a = repo.findById(session.id()).orElseThrow();
        Session b = repo.findById(session.id()).orElseThrow();
        a.postUserMessage("from a", List.of(), T0.plusSeconds(1));
        b.beginMove(Location.CLOUD, T0.plusSeconds(1));

        repo.persist(a);
        assertThrows(StaleAggregateException.class, () -> repo.persist(b));
        assertEquals(SessionStatus.RUNNING, repo.findById(session.id()).orElseThrow().status());
    }

    @Test
    void savingTheSameNewSessionTwiceIsRejected() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        Session session = Session.start(SessionId.fresh(), Location.LOCAL, T0);
        repo.persist(session);
        assertThrows(StaleAggregateException.class, () -> repo.persist(session));
    }

    @Test
    void sessionsComeBackMostRecentlyUpdatedFirst() {
        InMemorySessionRepository repo = new InMemorySessionRepository();
        Session old = Session.start(SessionId.fresh(), Location.LOCAL, T0.minusSeconds(100));
        Session recent = Session.start(SessionId.fresh(), Location.CLOUD, T0);
        Session middle = Session.start(SessionId.fresh(), Location.LOCAL, T0.minusSeconds(50));
        repo.persist(old);
        repo.persist(recent);
        repo.persist(middle);

        assertEquals(List.of(recent.id(), middle.id(), old.id()),
                repo.findAllByMostRecentlyUpdated().stream().map(Session::id).toList());
    }

    // --- connected folders -------------------------------------------------------------------------------------

    @Test
    void connectedFoldersAreSavedPerUserWithTheSameVersionRules() {
        InMemoryConnectedFoldersRepository repo = new InMemoryConnectedFoldersRepository();
        assertTrue(repo.findByUser(UserId.DEMO).isEmpty());

        ConnectedFolders folders = ConnectedFolders.none(UserId.DEMO);
        folders.connect(List.of(new FolderId("f1")));
        repo.persist(folders);

        ConnectedFolders a = repo.findByUser(UserId.DEMO).orElseThrow();
        ConnectedFolders b = repo.findByUser(UserId.DEMO).orElseThrow();
        assertEquals(List.of(new FolderId("f1")), a.folderIds());
        assertEquals(1L, a.version());

        a.connect(List.of(new FolderId("f2")));
        b.connect(List.of(new FolderId("f3")));
        repo.persist(a);
        assertThrows(StaleAggregateException.class, () -> repo.persist(b));
        assertEquals(List.of(new FolderId("f1"), new FolderId("f2")),
                repo.findByUser(UserId.DEMO).orElseThrow().folderIds());
    }
}
