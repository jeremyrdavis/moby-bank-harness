package com.mobybank.harness.application;

import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AppUser;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
import com.mobybank.harness.domain.ResultTable;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.StaleAggregateException;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.domain.UserRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Test doubles for the ports the application layer talks to. */
final class Fakes {

    private Fakes() {
    }

    /** Keeps a stored copy per session and hands out fresh copies, enforcing the optimistic-version contract. */
    static final class SessionRepo implements SessionRepository {

        private final Map<SessionId, Session> stored = new LinkedHashMap<>();

        @Override
        public Optional<Session> findById(SessionId id) {
            return Optional.ofNullable(stored.get(id)).map(s -> copy(s, s.version()));
        }

        @Override
        public List<Session> findAllByOwner(UserId owner) {
            return findAllByMostRecentlyUpdated().stream().filter(s -> s.owner().equals(owner)).toList();
        }

        @Override
        public List<Session> findAllByMostRecentlyUpdated() {
            return stored.values().stream()
                    .map(s -> copy(s, s.version()))
                    .sorted(Comparator.comparing(Session::updatedAt).reversed())
                    .toList();
        }

        @Override
        public void persist(Session session) {
            Session existing = stored.get(session.id());
            long storedVersion = existing == null ? 0L : existing.version();
            if (storedVersion != session.version()) {
                throw new StaleAggregateException("session", session.id().value().toString(), session.version(),
                        storedVersion);
            }
            stored.put(session.id(), copy(session, session.version() + 1));
        }

        private static Session copy(Session s, long version) {
            return Session.rehydrate(s.id(), s.owner(), s.title(), s.location(), s.status(), s.moveTarget(),
                    s.messages(), s.started(), s.createdAt(), s.updatedAt(), version);
        }
    }

    static final class FoldersRepo implements ConnectedFoldersRepository {

        private final Map<UserId, ConnectedFolders> stored = new HashMap<>();
        int persistCount;

        @Override
        public Optional<ConnectedFolders> findByUser(UserId userId) {
            return Optional.ofNullable(stored.get(userId))
                    .map(f -> ConnectedFolders.rehydrate(f.userId(), f.folderIds(), f.version()));
        }

        @Override
        public void persist(ConnectedFolders folders) {
            persistCount++;
            stored.put(folders.userId(),
                    ConnectedFolders.rehydrate(folders.userId(), folders.folderIds(), folders.version() + 1));
        }
    }

    static final class Catalog implements DocumentCatalog {

        static final FolderId F1 = new FolderId("f1");
        static final FolderId F2 = new FolderId("f2");
        static final FolderId F3 = new FolderId("f3");

        @Override
        public List<Folder> folders(UserId user) {
            return List.of(
                    new Folder(F1, "Earnings 2026", "Credit Research / Coverage / Earnings 2026", 24),
                    new Folder(F2, "Client Financials", "Corporate Banking / Clients / Financials", 58),
                    new Folder(F3, "Peer Comps", "Credit Research / Models / Peer Comps", 12));
        }

        @Override
        public List<CatalogFile> files(UserId user, FolderId folderId) {
            if (folderId.equals(F1)) {
                return List.of(new CatalogFile(F1, "Fathom_Q2_2026_10-Q.pdf"), new CatalogFile(F1, "Kestrel_Q2.pdf"));
            }
            if (folderId.equals(F3)) {
                return List.of(new CatalogFile(F3, "Industrials_Peer_Comps_Q2.xlsx"));
            }
            return List.of();
        }

        /** What OneDrive "holds", by file name, for the tests of attaching files. */
        final Map<String, byte[]> content = new HashMap<>();
        /** When set, every call asks the user to sign in with Microsoft. */
        boolean signInRequired;

        @Override
        public Optional<byte[]> fetch(UserId user, String fileName) {
            if (signInRequired) {
                throw new com.mobybank.harness.domain.SignInRequiredException("Sign in with Microsoft to use your OneDrive.");
            }
            return Optional.ofNullable(content.get(fileName));
        }
    }

    /** Who is calling: the test sets it. */
    static final class Caller implements CurrentUser {

        UserId user = UserId.DEMO;

        @Override
        public UserId id() {
            return user;
        }
    }

    static final class Users implements UserRepository {

        private final Map<UserId, AppUser> stored = new HashMap<>();

        @Override
        public Optional<AppUser> findById(UserId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            String normalized = AppUser.normalize(email);
            return stored.values().stream().filter(u -> u.email().equals(normalized)).findFirst();
        }

        @Override
        public Optional<AppUser> findByMicrosoftSubject(String subject) {
            return stored.values().stream().filter(u -> u.microsoftSubject().filter(subject::equals).isPresent())
                    .findFirst();
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

    /** Not a real hash: "hash:" in front of the password, so tests can see what was stored. */
    static final class PlainHasher implements PasswordHasher {

        @Override
        public String hash(String password) {
            return "hash:" + password;
        }

        @Override
        public boolean matches(String password, String hash) {
            return hash.equals("hash:" + password);
        }
    }

    static final class LoginSessions implements LoginSessionStore {

        final Map<String, UserId> open = new HashMap<>();
        private int counter;

        @Override
        public String open(UserId user, java.time.Duration timeToLive) {
            String token = "token-" + (++counter);
            open.put(token, user);
            return token;
        }

        @Override
        public Optional<UserId> resolve(String token) {
            return Optional.ofNullable(open.get(token));
        }

        @Override
        public void close(String token) {
            open.remove(token);
        }
    }

    static final class GraphTokens implements UserGraphTokens {

        final Map<UserId, MicrosoftTokens> tokens = new HashMap<>();

        @Override
        public void remember(UserId user, MicrosoftTokens microsoftTokens) {
            tokens.put(user, microsoftTokens);
        }

        @Override
        public Optional<MicrosoftTokens> find(UserId user) {
            return Optional.ofNullable(tokens.get(user));
        }

        @Override
        public void forget(UserId user) {
            tokens.remove(user);
        }
    }

    /** Returns a scripted reply (or throws a scripted failure) and remembers every turn it was given. */
    static final class ScriptedAgent implements SandboxAgent {

        AgentReply reply = new AgentReply(
                List.of(new Step(StepKind.READ, "a.pdf"), new Step(StepKind.COMPUTE, "leverage(net_debt, ebitda)")),
                List.of("Leverage rose to 2.9x."),
                Optional.of(new ResultTable(List.of("Metric", "Value"), List.of(List.of("Net leverage", "2.9x")))));
        RuntimeException failure;
        final List<AgentTurn> turns = new ArrayList<>();

        @Override
        public AgentReply runTurn(AgentTurn turn, Consumer<Step> onStep) {
            turns.add(turn);
            if (failure != null) {
                throw failure;
            }
            reply.steps().forEach(onStep);
            return reply;
        }
    }

    /** Reports the two stages the prototype shows, or throws a scripted failure. */
    static final class ScriptedTransfer implements SandboxTransfer {

        RuntimeException failure;
        final List<MoveRequest> requests = new ArrayList<>();

        @Override
        public void move(MoveRequest request, Consumer<MoveProgress> onProgress) {
            requests.add(request);
            onProgress.accept(new MoveProgress(MoveStage.PACKAGING, "Packaging context"));
            if (failure != null) {
                throw failure;
            }
            onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, "Transferring files"));
        }
    }

    static final class RecordingEvents implements SessionEventStream {

        final List<SessionEvent> published = new ArrayList<>();

        @Override
        public void publish(SessionEvent event) {
            published.add(event);
        }

        @Override
        public Subscription subscribe(String sessionId, Consumer<SessionEvent> listener) {
            return () -> { };
        }

        List<String> types() {
            return published.stream().map(SessionEvent::type).toList();
        }
    }

    /** Holds background tasks until the test decides to run them. */
    static final class QueueRunner implements BackgroundRunner {

        private final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void run(Runnable task) {
            queue.add(task);
        }

        int pending() {
            return queue.size();
        }

        void drain() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    static final class Uploads implements UploadStore {

        final Map<String, byte[]> files = new HashMap<>();

        @Override
        public void store(SessionId sessionId, String fileName, byte[] content) {
            files.put(sessionId.value() + "/" + fileName, content);
        }

        @Override
        public Optional<byte[]> find(SessionId sessionId, String fileName) {
            return Optional.ofNullable(files.get(sessionId.value() + "/" + fileName));
        }
    }
}
