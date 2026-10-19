# Persistence

Conversations, the analyst's connected folders, uploaded files and the sandbox bookkeeping are stored in PostgreSQL,
so they survive a restart. This page says what is stored, how the code is arranged, and how to run it.

## What is stored

| Table | Entity | Holds |
|---|---|---|
| `agent_session` | `SessionEntity` | One row per `Session`: title, location (`LOCAL`/`CLOUD`), status, move target, whether it has started, timestamps and the aggregate's `version` |
| `session_message` | `MessageEntity` | The conversation: the analyst's messages (the commands, with the files attached) and the agent's replies, in order. The files, steps, paragraphs and result table are JSON columns |
| `connected_folders` | `ConnectedFoldersEntity` | The folder ids each user has connected, in order, and the `version` |
| `upload` | `UploadEntity` | The bytes of each uploaded file, keyed by session and file name |
| `sandbox_record` | `PostgresSandboxRecordStore` / `SandboxRecordEntity` | Which sandbox backs each session (location, name, generation), the agent's own conversation id, and whether the sandbox still needs the shared files copied in |

The conversation the issue calls an *AgentConversation* is the `Session` aggregate: its commands are the messages, its
documents are the files on them, and it knows whether it runs locally or in the cloud. The sandbox bookkeeping is kept
beside it in `sandbox_record`.

## How the code is arranged

Everything database-related is in `infrastructure/persistence`; the domain and application layers do not import
`jakarta.persistence` (the layering tests check it).

- The **ports** are unchanged: `SessionRepository`, `ConnectedFoldersRepository` (domain) and `UploadStore`
  (application). `SandboxRecordStore` is the port of the `sbx` adapter's `SandboxRegistry`.
- The **entities** are separate classes from the aggregates, so the domain stays plain Java. A **mapper** converts
  between them using the aggregates' `rehydrate` methods.
- A **Panache repository** per entity (`SessionJpaRepository`, ...) does the queries. The `Postgres...` classes
  implement the ports on top of them.
- **Versioning.** `persist` locks the row (`PESSIMISTIC_WRITE`), compares the stored version with the aggregate's,
  writes, and advances it by one. A mismatch, or a lost race to insert the same new session, throws
  `StaleAggregateException`, which the API reports as 409. After a save, reload before changing the aggregate again.
- **Transactions** are one per repository method and never span an agent turn or a move (see
  [architecture.md](architecture.md)).
- The **demo data** is seeded only when there are no conversations, so a restart does not add it again.

## Running it

- **Dev mode and tests:** Quarkus Dev Services starts a `postgres:17` container; nothing to configure, but **Docker
  must be running**. Dev mode keeps the data between restarts while the container lives; the tests recreate the schema
  at every application start (`%test.quarkus.hibernate-orm.schema-management.strategy=drop-and-create`).
- **Anywhere else:** set `QUARKUS_DATASOURCE_JDBC_URL` (for example `jdbc:postgresql://db:5432/harness`),
  `QUARKUS_DATASOURCE_USERNAME` and `QUARKUS_DATASOURCE_PASSWORD`.
- **No migration tool.** Hibernate creates the tables and adds missing columns (`schema-management.strategy=update`);
  it never drops anything and cannot change an existing column. A schema change that needs more than adding a column
  means adding Flyway.

## Testing

`PostgresRepositoriesTest` runs the repositories against a real database: round trips of a conversation with files,
steps, table, notice and location, message order, history order, the version rules, two concurrent writers (exactly one
wins), connected folders, uploads and sandbox records. The application tests keep using in-memory test doubles of the
ports (`Fakes`, `InMemory...` in `src/test`), so they need no database.
