# Authentication

Analysts sign in with a work email and password, or with Microsoft. Whichever they use, the app knows who they are, shows
only their own conversations, and (after a Microsoft sign-in) reads *their* OneDrive.

## Modes

`harness.auth.mode` chooses:

| Mode | What happens |
|---|---|
| `login` (default) | Every page and API call needs a signed-in user. Anyone else is sent to /login.html (the page) or gets 401 (the API) |
| `none` | Everybody is the demo user, as before. The automated tests use this unless a test is about signing in |

## The sign-in page

`login.html` (at the repository root, next to `index.html`) is public. Its form posts the email and password to
`POST /auth/login`; **Sign in with Microsoft** goes to `GET /auth/microsoft` and is shown only when
`GET /auth/options` says Microsoft is set up. Failures return to the page with an `error` code it words for the user.

Both ways end in the same session: a random 256-bit token in the `moby-login` cookie (HttpOnly, `SameSite=Lax`, and Secure over https). The database keeps only a SHA-256 hash of the token and when it expires
(`harness.auth.session-ttl`, default 8 hours). **Sign out** (the button in the sidebar card) ends the session and forgets
the user's Microsoft tokens.

`index.html` is not a public file: `PageResource` serves it at `/` and /index.html to signed-in users only.

## Username and password

`harness.auth.seed-users` (on by default) creates three analysts at startup, each only if no account has its email yet
(so it also works on a database that already has users, and never changes an account that exists):

| Email | Name | Password | Owns |
|---|---|---|---|
| `hermione.granger@mobybank.com` | Hermione Granger | `moby-demo` (`harness.auth.seed-password`) | the demo conversations and connected folders |
| `rana.kapoor@mobybank.com` | Rana Kapoor | `moby-demo` | nothing, so she shows that users cannot see each other's work |
| `jeremy.davis@docker.com` | Jeremy Davis | `myawesomepassword` (`harness.auth.seed-developer-password`) | nothing yet: the developer's own account |

The passwords are public in this repository: **change them or turn seeding off anywhere that is not a demo** (the app warns
at startup when it runs normally with the defaults). Passwords are stored as bcrypt hashes. The same message answers an
unknown email and a wrong password, and five failures for one email lock it out for 15 minutes.

## Sign in with Microsoft

The authorization-code flow with PKCE is done by the Quarkus OpenID Connect extension, not by our code. It protects only
`/auth/microsoft` (and its callback `/auth/microsoft/callback`). When it has authenticated the user, `AuthResource` finds or
creates the account for the Microsoft identity (an existing account with the same email is linked), creates the normal
session cookie, remembers the user's Graph tokens, and redirects to `/`.

### Registering the app in Microsoft Entra

1. Sign in at <https://entra.microsoft.com> and open **Identity → Applications → App registrations → New registration**.
2. Name it, for example `Moby Bank Analyst Agent (demo)`. For **supported account types** choose *this organizational
   directory only* (a tenant), *personal Microsoft accounts only*, or *any organizational directory and personal
   accounts*.
3. **Redirect URI**: platform **Web**, `http://localhost:8088/auth/microsoft/callback` (use your own host and port).
4. Copy the **Application (client) ID** (and, for a tenant, the **Directory (tenant) ID**).
5. **Certificates & secrets → New client secret**; copy the secret's **Value** at once.
6. **API permissions → Add a permission → Microsoft Graph → Delegated:** `openid`, `profile`, `offline_access`,
   `Files.Read` (and `User.Read`, added by default). Grant admin consent if you administer the tenant.

### Configuration

Put these in the environment (or a git-ignored `.env` file in the module, which dev mode reads):

```
HARNESS_MICROSOFT_ENABLED=true
HARNESS_DOCUMENTS_MODE=graph
QUARKUS_OIDC_CLIENT_ID=<application id>
QUARKUS_OIDC_CREDENTIALS_SECRET=<secret value>
# a tenant:
QUARKUS_OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/<tenant id>/v2.0
# or personal accounts (the token issuer then differs per user):
# QUARKUS_OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/consumers/v2.0   (or /common/v2.0)
# QUARKUS_OIDC_TOKEN_ISSUER=any
```

### If sign-in with Microsoft does not work

- **The app will not start and the message names `QUARKUS_OIDC_AUTH_SERVER_URL`:** the setting is missing from the
  environment or `.env`. Microsoft sign-in needs it (and `QUARKUS_OIDC_CLIENT_ID`) whenever `HARNESS_MICROSOFT_ENABLED` is
  true. Dev mode never starts a Keycloak of its own for this (`quarkus.keycloak.devservices.enabled=false`); without that
  setting the sign-in would redirect your browser to a Keycloak container that nobody can reach, and Keycloak rejects the
  Graph scopes with `invalid_scope`.
- **The sign-in page shows "Microsoft sign-in did not complete":** Microsoft refused, you declined, or the flow failed.
  The reason is in the server log (`Microsoft sign-in failed: ...`); `AuthResource` handles it at `/auth/error`.
- **Redirect URI mismatch** (an Entra error page): the URI in the app registration must match exactly, including scheme,
  host and port, for example `http://localhost:8088/auth/microsoft/callback`.

## Who owns what

Every session has an owner (`Session.owner()`). The use cases take the caller from `CurrentUser`, so
`SessionApplicationService` lists only the caller's sessions, and someone else's session is **404, never 403**, so ids
cannot be probed. Background work (a turn, a move, recovery after a restart) acts on a session by id and has no caller.
Rows from before there were users have no owner and belong to the demo user (Hermione). Connected folders were already
per user.

## Requests

- `/api/*` and `/ui/*` need the cookie in `login` mode: no valid cookie gives 401 JSON (and, for `/ui`, an `HX-Redirect`
  to the sign-in page). `/auth/*`, /login.html, the static files and `/q/*` are open.
- Requests that change something must come from the page (`HX-Request`) or carry JSON, which a form on another site
  cannot send. This is on top of `SameSite=Lax`.
- `AuthFilter` finds the user; `RequestIdentity` carries the user's id for the request; `RequestCurrentUser` is the
  `CurrentUser` the use cases ask.
