# Plan: moby-cars re-skin for the Cox Automotive meeting

## Context

The demo is shown to Cox Automotive (owner of autotrader.com). The harness stays the same; only its skin changes so it looks like it was built for them:

1. No "Sign in with Microsoft" on the login page.
2. A used-car **quarterly report** story: one report already in the conversation history, and source documents in the (fake) OneDrive so a *new* chat can produce the next quarter's report.
3. The site is called **moby-cars**, and the analyst is an auto-industry analyst, not a bank credit analyst.

Decisions from shaping (all confirmed with the user):

| Question | Decision |
|---|---|
| Report form | Seeded conversation with the previous quarter's report (history) **plus** source files in a connected folder so a new chat produces the next report |
| Persona | Full re-theme: auto-industry analyst, used-car folders, files and conversations |
| Rename depth | User-visible only. Leave Java package `com.mobybank.harness`, sandbox name prefix `moby-bank-<id>-g<n>`, module folders, `artifactId`. Cookies stay too (invisible) |
| Microsoft | Remove from the UI only. Backend OIDC/Graph code stays, switched off by config (`harness.auth.microsoft.enabled=false` is already the default) |
| Visuals | None. Keep Docker/Trident look; logo mark "MB" becomes "MC" |
| Logins | Seeded users move to `@mobycars.com` |
| Standards | `agent-os/standards/index.yml` is empty, so none apply |
| Product alignment | Mission is unchanged (engineering teams evaluating a custom agent harness); this is a cosmetic skin for one audience, so no product docs change except the persona wording |

Out of scope: downloadable report files, multi-table messages, new UI components (the UI renders prose paragraphs + one table per message, and nothing else; no markdown, no assistant-side file chips), the React prototype in `moby-bank-prototype/`, renaming Java packages/sandbox prefix.

## Task 1: Save spec documentation

Create `agent-os/specs/2026-11-16-1407-moby-cars-reskin/` with:

- **plan.md**: this plan
- **shape.md**: scope, the decisions table above, context (visuals: none; references below; product alignment note)
- **standards.md**: "No standards indexed; none apply."
- **references.md**: `DemoData.java` session `c1` as the template for the seeded report conversation; `FakeSandboxAgent.answer`; `login.html`; `agent-os/specs/2026-10-01-1322-htmx-ui-on-quarkus/` as the prior UI spec
- **visuals/**: empty (none provided)

## Task 2: Remove the Microsoft sign-in from `login.html`

File: `login.html` (repo root; copied to `META-INF/resources` by the `copy-login-page` execution in `moby-bank-quarkus/pom.xml`).

- Delete the `#sso` button and its logo SVG (lines ~70-73) and the "or use your bank credentials" divider (line ~74).
- Delete the `/auth/options` fetch that shows/hides the button (~114-117), the `$('sso')` references in `busy()` (~120) and the `onclick` (~122), and the `microsoft` / `unavailable` error strings (~104-105).
- Leave the backend (`AuthResource`, `/auth/options`, `MicrosoftSignInCheck`, `harness.auth.microsoft.*`, `quarkus-oidc`). Nothing in tests or docs changes, so `DocumentationTest` stays green. A `GET /auth/microsoft` still answers (inert while the feature is off), which is acceptable for the demo.
- `index.html` has no Microsoft sign-in UI; its sign-out button is the generic `POST /auth/logout` and stays.

## Task 3: Rename the visible brand to moby-cars

Visible strings only. Files and places (from exploration):

- `index.html`: `<title>` (line 6), sidebar brand `Moby Bank / Analyst Agent` (135) and the "MB" mark (134) → `moby-cars` / `MC`; empty-state text and composer placeholder (179, 185) → used-car wording; the "Moving session to Moby Private Cloud…" toast (291) → `moby-cars Private Cloud`.
- `login.html`: title (6), heading and subtitle (67), "bank credentials" text (116), placeholder `name@mobybank.com` → `name@mobycars.com` (78), the staff-only notice (88), copyright (92).
- Qute templates in `moby-bank-quarkus/src/main/resources/templates`: `topbar.html:6` ("Moby Private Cloud"), `main.html:8,16` (empty state, placeholder). Do not touch the `{@com.mobybank…}` type declarations.
- Persona: role `"Credit Research"` → e.g. `"Used-Vehicle Analytics"` in `application.properties:3` (`harness.user.role`), `CurrentUserApplicationService.java:29`, `DemoUserSeeder.java:127-129`; real-agent PREAMBLE in `infrastructure/sbx/SbxSandboxAgent.java:40-41` ("credit-research assistant helping a bank analyst" → used-vehicle market analyst at moby-cars). Leave the "Analyst:" / "Analyst request:" prompt tokens (asserted in `SbxSandboxAgentTest:155`).
- Seeded users: `hermione.granger@mobybank.com` / `rana.kapoor@mobybank.com` → `@mobycars.com`, in `DemoUserSeeder.java` (127-128). Keep the developer account `jeremy.davis@docker.com`, passwords and the `DEMO` user binding as they are. Names stay; update the same emails in the tests listed in Task 6.
- Titles and docs: OpenAPI title (`application.properties:59`, `openapi.yaml:577`; regenerated by the build), Javadoc titles (`pom.xml:187-188`, `src/main/javadoc/overview.html`), root `README.md`, `moby-bank-quarkus/README.md`, and the user-facing parts of `CLAUDE.md` (login emails). Keep folder and module names in docs.

Not renamed on purpose: Java package/groupId, `moby-bank-` sandbox prefix (`SandboxRegistry.java:115`; renaming orphans existing sandboxes and breaks ~80 assertions), cookies `moby-login`/`moby-current`, theme key `moby-agent-theme`, `artifactId`/folders, the `"moby-bank-demo/"` UUID seed.

## Task 4: Re-theme the demo data around used cars

File: `moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/DemoData.java` (reuse its helpers `user(...)`, `assistant(...)`, `read(...)`, `compute(...)`).

**Library (`LIBRARY`, lines ~41-57), replacing the six bank folders**, all with the same count so counts in tests hold:

| Folder | Files (fictional) |
|---|---|
| Used Vehicle Sales | `Used_Vehicle_Sales_Q3_2026.xlsx`, `Used_Vehicle_Sales_Q2_2026.xlsx` |
| Wholesale Pricing | `Wholesale_Price_Index_Q3_2026.xlsx`, `Auction_Volumes_Q3_2026.pdf` |
| Inventory and Days-to-Turn | `Days_To_Turn_By_Segment_Q3_2026.xlsx` |
| Loan Performance | `Auto_Loan_Performance_Q3_2026.xlsx` |
| Quarterly Reports | `Used_Car_Quarterly_Report_Q2_2026.docx` (the finished history report) |
| Analyst Templates | `Quarterly_Report_Template.docx` |

`initiallyConnected()` (~74-76) connects the folders needed for the new-chat flow (Used Vehicle Sales, Wholesale Pricing, Inventory and Days-to-Turn) and leaves others for the "connect a folder" demo, matching today's f1-f3 pattern.

**Seeded conversations (`sessions(Clock)`, ~79-155).** Keep the structure and ordering of the five sessions (so time grouping `TODAY, TODAY, PREV7, PREV7, EARLIER` and the shape of c1 hold) but change the content:

- **c1 (the history report):** "Used cars: Q2 2026 quarterly report". Copy c1's shape: a user message attaching the Q2 sales/wholesale/turn files; an assistant reply with 5 steps (`read` ×3 `OneDrive/…` + 2 `compute`, e.g. `days_to_turn(by_segment)`, `delinquency_rate(30d,60d)`), 2 narrative paragraphs (volume, pricing trend, turn time, loan performance, risks), one 4-row `ResultTable` (Segment, Units, Avg retail price, Wholesale change QoQ, Days to turn); a follow-up question and second reply. 3 files attached in total (as today). Numbers are clearly fictional.
- **c2-c5:** four shorter used-car conversations (e.g. "EV used-price softness", "Wholesale-to-retail spread by segment", "Days-to-turn outliers: trucks", "Subprime auto delinquency watch"), modelled on the current c2-c5.

Seeded users keep ownership of the demo conversations as today (Hermione's account; Rana sees none).

## Task 5: Make the fake agent talk used cars (new-chat flow)

File: `infrastructure/FakeSandboxAgent.java` (`answer`, lines ~94-108; `COMPUTE_LABEL` line ~31). It ignores the user's text and branches only on whether files are attached; keep that.

- `COMPUTE_LABEL` → `build_quarterly_report(used_sales, wholesale, days_to_turn)`.
- No files: canned prose that says it can work from the used-vehicle files in the connected OneDrive folders (lines ~96-98).
- Files attached: "I read X, Y and Z and put together the Q3 2026 used-car quarterly report" with 2-3 paragraphs (headline volume, pricing/wholesale trend, turn time, risks) and a fixed 4-row segment `ResultTable`. The result: attach the Q3 source files in a new chat and the reply is the Q3 report, which can be compared against the Q2 report in history.
- Real `sbx` mode is unaffected except for the PREAMBLE text change in Task 3.

## Task 6: Update tests and docs for the new strings

Tests that assert on changed text (from exploration; update expected values, keep the structural assertions):

- `DemoDataTest` (titles list lines 35-37; folder/file names), `WiringTest` (titles 48-49, `library().size()`, user names, file name line 73), `FakeAdaptersTest` (six folder names 209-215, file name and fetch content 279-280, fake-reply prefix line 59)
- `WebTest` ("What should we analyze?" lines 83, 129), `LoginTest` (login page heading line 51, emails 26-27/118/144/150, role line 129)
- `ApiTest:69` and `CurrentUserApplicationServiceTest:23` (role), `FolderApplicationServiceTest:80`, application `Fakes.java:111-113` (folder path "Credit Research / Coverage / …"), `DemoUserSeederTest`, `PostgresAuthRepositoriesTest:98-102`, `AuthenticationApplicationServiceTest`, `MicrosoftSignInTest:185` (emails). `AppUserTest` only normalises case; leave it unless it names the domain.
- The Graph tests (`GraphModeTest`, `GraphDocumentCatalogTest`) use their own `FakeGraphServer` fixtures, so check but expect no change.
- Docs: `DocumentationTest` checks docs against code, so update anything that quotes the changed emails, roles, folder names or settings (`docs/authentication.md` seeded-users table, `docs/ONBOARDING.md`, `docs/ui-fragments.md`, `docs/testing.md` where they cite demo data) and `CLAUDE.md`. Regenerate Javadoc: `./mvnw javadoc:javadoc`. Regenerate/commit `openapi.yaml` (title only; the build writes it).
- The React prototype (`moby-bank-prototype/`) is untouched. Its tests assert "Moving session to Moby Private Cloud…" in its own UI. Run `npm test` there to confirm nothing broke; `test:live` seeds from the Quarkus demo data, so check what it asserts before relying on it.

## Verification

1. `cd moby-bank-quarkus && ./mvnw test` (needs Docker for Dev Services; run via `bash -c`). Expect all green, including `DocumentationTest` and the layering tests.
2. `cd moby-bank-prototype && npm test` still passes.
3. Run the app: `./mvnw quarkus:dev` (after editing `../index.html` or `../login.html` in dev mode, run `./mvnw resources:copy-resources@copy-index-page` and `@copy-login-page`). The seeder only seeds an empty database, so use a fresh Dev Services DB.
4. In a browser (publish port 8080 through `sbx port publish` if needed):
   - `/login.html`: no Microsoft button or "or" divider; title/heading say moby-cars; sign in as `hermione.granger@mobycars.com` / `moby-demo`.
   - Sidebar shows moby-cars / MC and the new role; history lists the used-car conversations, the first being the Q2 quarterly report with steps, paragraphs and a table.
   - New chat: connect/attach the Q3 files from the fake OneDrive, send "Produce the Q3 quarterly report"; the reply is the used-car Q3 report. Try Run in cloud / Move to local to confirm the topbar text.
   - `rana.kapoor@mobycars.com` sees an empty history.
5. Grep for stragglers in visible places: `grep -rniE "moby ?bank|credit|bank|mobybank" index.html login.html moby-bank-quarkus/src/main/resources/templates`.
