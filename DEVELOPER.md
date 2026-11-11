# Documentation for developers of this repo

### Install GraalVM (for Mac)

You can follow [official installation](https://www.graalvm.org/latest/docs/getting-started/macos/) steps
or by using [sdkman](https://sdkman.io) bellow, which can be installed
with [homebrew](https://github.com/sdkman/homebrew-tap).

```bash
sdk env install
sdk env
```

To check whether the installation was successful, run

```bash
$ java -version
java version "24" 2025-03-18
Java(TM) SE Runtime Environment Oracle GraalVM 24+36.1 (build 24+36-jvmci-b01)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 24+36.1 (build 24+36-jvmci-b01, mixed mode, sharing)
```

### Run tests

```bash
./gradlew test
```

### OpenAPI spec

The API contract is committed as `openapi/cabin-visits.json` and used by API clients (for example the
MMM-CabinStats MagicMirror module) to generate their types. `OpenApiSpecTest` fails if the spec served at
`/openapi.json` differs from the committed file. After an intended API change, regenerate it and commit the diff:

```bash
UPDATE_OPENAPI=true ./gradlew test --tests '*OpenApiSpecTest' --rerun
```

The spec is inferred from the code when it is compiled, and an incremental build can leave it stale (for example a
missing request body after `formatKotlin` changed a file). If `OpenApiSpecTest` fails without an API change, or the
regenerated spec looks wrong, run `./gradlew clean` first and regenerate again.

### Layering and package boundaries

Dependencies point one way: route (controller) -> service -> repository -> table (entity).

- A route only calls services. A service only calls repositories of its own package, and services of other packages. A
  repository only touches its own tables.
- **Across packages, call the other package's service, not its repository.** A package owns its data and the rules around it
  (validation, versions, overlap, derived data).
- **Where does a read for another package live?** In a service, never by injecting the repository. Do not split reads
  from writes by default: small reads without logic do not justify a class of their own. In this order:
  1. **In the existing service of the package** when it is a natural home and cheap to depend on. Example: the avatar
     reads are in `GuestAvatarService`, which already owns avatars.
  2. **In a thin `...QueryService`** only when the existing service is a poor dependency or there is no natural home:
     - The existing service is heavy to depend on: it loads configuration, needs an HTTP client or runs imports and sync.
       Example: `VehicleTripService` and `CheckInOutSensorService`, so `VehicleTripQueryService` and
       `CheckInOutSensorQueryService` exist.
     - No existing service fits the purpose of the read. Example: `GuestQueryService` for `allGuests` and `guestsByEmail`,
       because the other guest services each own one kind of write.
  3. **In a second service** when the reads must not follow the same rules as the user-facing service, or when a single
     class would create a dependency cycle. Example: see the next bullet.
- **Reservations have two services.** `ReservationService` serves users: it takes the caller and limits what a guest user
  sees. `ReservationSystemService` is for tasks that act for the system (the calendar push, the derivation of check-in/out,
  statistics): it has no caller and applies **no visibility rule**, so never use it from a route to answer a user's
  request. It also holds the derived-data writes (`setCheckIn`, `setCheckOut`, `addVehicleTripLink`). They are separate
  classes because `ReservationService` calls the derivation after a write, so a single class would make the derivation
  and the reservations depend on each other.
- Avoid dependency cycles between services: wire the other direction with a function parameter that `Application.kt` fills in
  (as `ReservationService` does with `pendingCalendarOperations` and `onTimesChanged`).
- **Be pragmatic.** Do not add a pass-through layer when it only adds code. Accepted exceptions, say why in a comment or in the
  PR when you take one:
  - Startup wiring in `Application.kt`.
  - Writes that must share one transaction with another package's write: the reservation repository enqueues the
    `calendar_outbox` row in the reservation transaction.
  - ORM relationships between entities: `ReservationEntity` links `GuestEntity` and `VehicleTripEntity`, and the
    reservation repository checks that the guests exist inside the write transaction, so it reads guest rows. A service
    cannot do that without a second transaction.
- Package cycles through shared domain types (`Reservation` and `CheckIn`/`CheckOut`, `guests` and `auth`) are accepted; do
  not add a new one through a service or a repository.

### API design principles

These apply to every new or changed endpoint. Permissions and status codes per endpoint are in
[docs/authentication.md](docs/authentication.md).

1. **Create is `POST` on the collection.** The server generates the id. The answer is `201` with the created resource.
2. **Update is `PUT` on the resource, a full replace.** The body has every writable property and the `version` the
   client read. A missing property is `400`, `null` clears an optional property (`email`, `role`), and read-only or unknown
   properties are ignored. The answer is `200` with the new representation. We do not use `PATCH`.
3. **Every update and delete carries the `version`** (in the body, and as `?version=` on `DELETE`). A stale one is `412`, a
   missing or non-numeric one is `400`. The repository compares it in the same transaction as the write. An update that
   changes nothing is `200` with the unchanged `version`.
   *The one exception is the avatar upload*: a blind overwrite that does not depend on the current state, so it has no `version`.
4. **Roles with different rights get separate endpoints**, not field-level rules on one endpoint: `PUT /api/me` replaces
   the user's own profile fields, `PUT /api/guests/{guestId}` replaces everything and is admin only.
5. **Request rules.** Write routes read the body with `receiveJsonBody<T>()` (`common/RequestBody.kt`), not `call.receive`:
   `application/json` is required (`415`) and at most 64 KB is read (`413`). The avatar upload is the one exception: it
   reads raw bytes with `receiveBinaryBody` and requires `image/jpeg` (`415`) and at most 4 MB (`413`). No state change on `GET`,
   no body-less `POST`, and no CORS plugin, which together keep cross-site requests out.
6. **Errors.** Every route answers `ApiError(code, message)` with a stable `code`; one route never mixes JSON and plain-text
   errors.
7. **Rules live in services, not in routes.** A route maps the result of the service to a status code.
8. **OpenAPI.** The plugin only infers a request body from `call.receive<T>()`, so declare it in `describe {}` with
   `requestBody { required = true; schema = jsonSchema<T>() }`. For a full update use `.allPropertiesRequired()`
   (`common/OpenApiSchemas.kt`), because the generator leaves nullable properties out of `required`. `OpenApiSpecTest` fails for a
   `put` or `post` without a request body and for a response without a description. Regenerate the spec instead of editing it (see above).
    `ResponseCodeAudit` (in the test sources, run by `ProjectConfig` after the whole suite) fails when a status code that the
    spec documents is answered by no test, so a new `describe {}` code needs a test. Test apps must be configured with
    `configureTestApiRoutes` (or `recordResponses()`) to be counted. It is skipped when the run is limited with `--tests`.
9. **Native image.** A new `@Serializable` class needs its reachability entries, see `CLAUDE.md`.
10. **Every property is documented.** Every property of a request or response class (and every path or query parameter)
    has a description in the OpenAPI spec: `@JsonSchema.Description("...")` on the property of a `@Serializable` class
    (see `MeResponse`, `GuestUpdate`), `description` on a parameter in `describe {}`. Say what the value means, its limits and
    format, when it is `null`, and what it must not be confused with (for example `version` is the guest version, not the
    avatar version). A property without a description is a gap in the contract that clients generate their types from.
    `OpenApiSpecTest` fails for a property or parameter without a description. A property that refers to another class
    (`$ref`) is documented by the description of that class, because a description on the property makes the generator
    emit a duplicate schema.
11. **Every property has an example.** Every property of a request or response class (and every path or query parameter)
    has a realistic `example` in the OpenAPI spec, so clients and the generated docs show real values: use
    `@JsonSchema.Example("...")` on the property and `example = GenericElement(...)` on a parameter in `describe {}` (see
    `ReservationResponse`, `listReservations`). The annotation value is a **JSON literal**: a string needs its own quotes
    (`@JsonSchema.Example("\"2026-07-10T16:00:00Z\"")`), a number does not (`@JsonSchema.Example("3")`). Otherwise the
    generator writes `Failed to resolve schema` into the spec instead of failing; `OpenApiSpecTest` checks for that.
    Use values that look like production data and are valid for the property (an ISO instant for a time, an existing
    enum value, a real-looking id), and keep related examples consistent (`endTime` after `startTime`). A property that refers to
    another class, or to a list of one, takes its examples from that class. `OpenApiSpecTest` fails for a
    property or parameter without an example, and for an example that does not match the type or the enum values of its
    property.

### Authorization and restricting endpoints by role

Setup of Cloudflare Access, the status codes and how roles are granted are described in
[docs/authentication.md](docs/authentication.md). This section is about the code.

#### How a request is authorized

Authentication (who is calling) and authorization (what may they do) are separate steps:

1. **Authentication** (`CloudflareAccessAuthentication.kt`): the Ktor `jwt` provider `cloudflare-access` verifies the
   HTTP header `Cf-Access-Jwt-Assertion` against Cloudflare's signing keys (signature, issuer, audience, expiry). The `email`
   claim is then looked up with `AppUserService.userByEmail` which matches the `guest` row that has a `role` through `GuestRepository.guestsByEmail`.
   The result is a `UserPrincipal` (id, name, firstName, lastName, birthYear, gender, email, isFamily, role).
   Machines authenticate with the Ktor `apiKey` provider `api-key` (`ApiKeyAuthentication.kt`): the HTTP header `X-Api-Key`
   is hashed with SHA-256 and compared in constant time with the hashes in `API_KEYS`. The result is a
   `ServicePrincipal` (name), which has no role.
2. **Authorization** (`RoleAuthorization.kt`): a `UserPrincipal`'s role is compared with the route's allowed roles. A
   `ServicePrincipal` is only let through on routes with `allowService = true`.

| Situation                                              | Response |
|--------------------------------------------------------|----------|
| Cloudflare Access is not configured (env vars missing) | 503      |
| Token or API key missing, invalid or expired           | 401      |
| Valid API key, but the route does not allow services   | 403      |
| Valid token, but the email has no role in the app      | 403      |
| Valid user, but the role is not allowed on the route   | 403      |
| Valid user with an allowed role                        | handler  |

The roles (`AppRole`) are `ADMIN`, `FAMILY_USER` and `GUEST_USER`. They are stored in `guest.role` and changed with
`PUT /api/guests/{guestId}`. The role is read from the database on every request, so a changed role applies without
the user logging in again.

#### Restricting an endpoint

Wrap the routes in `authorized(...)` and list the roles that are allowed:

```kotlin
fun Application.configureReportRoutes() {
    routing {
        // Only ADMIN and FAMILY_USER, everyone else gets 403
        authorized(AppRole.ADMIN, AppRole.FAMILY_USER) {
            get("/api/reports") {
                val user = checkNotNull(call.principal<UserPrincipal>())
                call.respond(reportFor(user))
            }
        }
    }
}
```

- `authorized` always includes authentication, so a role check can never run without a verified token. Do not use
  `authenticate(CLOUDFLARE_ACCESS_AUTH)` alone for new endpoints, since it lets every user with any role through.
- Routes outside `authorized { }` stay open. Anything that must be protected has to be wrapped explicitly.
- Services (API key) are denied by default. Only a route that is meant for a machine passes `allowService = true`, for
  example `authorizedAnyRole(allowService = true) { ... }`. Keep that list as small as possible.
- Pass every role that may call the endpoint, for example `authorized(AppRole.ADMIN)` for admin-only. There is no role
  hierarchy, `ADMIN` does not imply `FAMILY_USER`.
- Register the route group in `configureApiRoutes` (`Application.kt`), after `configureAuthentication`.
- Describe 401/403/503 in the OpenAPI `describe { responses { ... } }` block, as `describeMe()` in `AuthRoutes.kt` does,
  and regenerate the spec (see above).

#### Changing guest data

`PUT /api/me` (every role, own guest, only firstName, lastName, birthYear and gender) calls
`GuestProfileService.updateOwnProfile(user, update)`, and `PUT /api/guests/{guestId}` (`authorized(AppRole.ADMIN)`)
calls `GuestProfileService.update(actor, guestId, update)`. The service decides what may be changed and validates the
values. The route only maps the result to a status. Do not add such rules to the routes, see
[docs/authentication.md](docs/authentication.md#granting-access).

#### Testing a restricted endpoint

`AuthRoutesTest` shows the pattern: install the routes with `testCloudflareAccess` and create tokens with
`accessToken(email = ...)` from `AccessTokenFixtures.kt`. These sign with a local test key, so no call to Cloudflare is
made. Store the guest with the wanted role through `SqliteGuestRepository`, then cover at least 401 (no or invalid
token), 403 (unknown user and a role that is not allowed) and the success case.

### Collect metadata with the Tracing Agent

GraalVM provides a Tracing Agent to easily gather metadata and prepare configuration files. The agent tracks all usages
of dynamic features during application execution on a regular Java VM.
Read more
at [Collect Metadata with the Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)

```bash
# Source all env variables from the .env file
set -a; source .env; set +a

# Run the app with the tracing agent (stop with Ctrl+C when done)
./gradlew collectNativeMetadata
```

The agent **merges** into the existing
[META-INF/native-image/main/reachability-metadata.json](META-INF/native-image/main/reachability-metadata.json)
(`config-merge-dir`), so a run only adds entries and never removes earlier ones. Call all REST endpoints and let the
sync jobs run (possibly in several runs), then review the diff before committing. Entries are never pruned
automatically; to start clean, delete the file and rerun.

### Build GraalVM native image

Build the GraalVm native image as an executable file, with use
of [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html).

Optional argument `-PnativeBuildArgs=""` can be used to add additional build arguments to the native image compilation.
Use comma to separate arguments.

```bash
./gradlew nativeCompile
```

The native image will be compiled to `./build/native/nativeCompile/graalvm-server`.

### Build GraalVM native image for tests

Build the GraalVm native image as an executable file for running tests as native image.
This means that tests will be compiled and executed as native code.
Read more [here](https://graalvm.github.io/native-build-tools/0.10.4/gradle-plugin.html#testing-support).

Optional argument `-PnativeBuildArgs=""` can be used to add additional build arguments to the native image
compilation. Use comma to separate arguments.

The native image will be compiled to `./build/native/nativeTestCompile/graalvm-test-server`.

```bash
./gradlew nativeTestCompile
```

### Check for available dependency updates

Overview of dependencies with version updates available:

```bash
./gradlew dependencyUpdates
```

### Kotlin linter

Perform Kotlin linter check

```bash
./gradlew lintKotlin     
```

Automatically correct linting warnings

```bash
./gradlew formatKotlin
```

### Static code analysis with Detekt

Run a detekt analysis and complexity report the source files with use
of [Detekt Gradle plugin](https://detekt.dev/docs/gettingstarted/gradle/).

Reports are generated in the folder `./build/reports/detekt`.

```bash
./gradlew detekt
```

### Operations

The database `data/app.db` (SQLite, journal mode `delete`) is the only source of reservations and guests: the Google Calendar is no longer read and
`guests.json` is no longer imported. Treat it as the data you must not lose.

#### Backup and restore

A scheduled job on the host takes a consistent online copy once a day (the app can keep running):

```shell
sqlite3 data/app.db ".backup '<backup-dir>/app-$(date +%F).db'"
```

- `<backup-dir>` is off the app volume. Keep about 30 daily copies and monitor that the job succeeds.
- Backups hold personal data (names, emails, birth years, avatars) for as long as they are kept.
- Restore (rehearse it into a copy, not into the live file):
  1. Stop the app.
  2. Copy the backup to `data/app.db` (keep the old file as `app.db.broken`).
  3. Start the app, then compare row counts with the backup:
     `sqlite3 data/app.db "select (select count(*) from reservation), (select count(*) from guest), (select count(*) from guest_avatar)"`.
  4. Check the Flyway version, see the runbook below.
- Test migrations against a copy of the database, never the live file.

#### Locked out: no admin can log in

The app refuses to remove the last `ADMIN` that has an email, but SQL or two admins acting at the same moment can still leave none. The way back is
SQL on a copy-checked, backed-up `data/app.db` (stop the app first, or accept that the change shows on the next request):

```shell
sqlite3 data/app.db "update guest set role = 'ADMIN' where id = '<guest id>' and email is not null"
```

The guest also needs an entry in the Cloudflare Access policy to log in.

If **no guest has the `ADMIN` role at all** (an empty or restored-empty database), no SQL is needed: set `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_FIRST_NAME`,
`BOOTSTRAP_ADMIN_LAST_NAME`, `BOOTSTRAP_ADMIN_BIRTH_YEAR` and `BOOTSTRAP_ADMIN_GENDER` and restart (`BootstrapAdmin`, see
[docs/authentication.md](docs/authentication.md#granting-access)). It creates the admin, or gives the role to the guest with that email, and does nothing once an
admin exists. Unset the variables afterwards.

#### SQLite locking and concurrent writes

The database is one SQLite file with a single writer: the API writes, the vehicle trip import, the check-in/out job, the
backup and the calendar outbox drain all contend for the same lock. `createSqliteDataSource`
(`plugins/SqliteDatabase.kt`) therefore uses `BEGIN IMMEDIATE` and a 5 s `busy_timeout`, so a writer waits for the
write lock when its transaction starts. Without it, two concurrent creates both read, then both want to write, and all
but one fail at once with `SQLITE_BUSY` instead of waiting (`ReservationConcurrencyTest` shows the difference: 7 of 8
creates failed). With it the loser sees what the winner committed and answers `409` (overlap).

- Pure reads must not use that `DataSource`: the mode is per `DataSource`, so they would take the write lock and queue
  behind each other (measured: 50 concurrent reads took up to 4 s, 10 took up to 0.2 s). Reads therefore use a second
  `DataSource` on the same file with the default deferred `BEGIN` (`createSqliteReadDataSource`), through
  `suspendReadTransaction`. They run concurrently with each other and with a writer (50 reads: 0.13 s). A read-only
  method uses `suspendReadTransaction`, a method that writes uses `suspendTransaction`; a read inside a write that
  must be consistent with it stays in the `suspendTransaction`.
- `journal_mode` stays `delete`. WAL did not help reads on its own (a read on the write `DataSource` still took the
  write lock) and only made writes next to reads faster, while it adds `-wal`/`-shm` files that a backup or a copy of
  `data/app.db` must include.
- A repository write that checks something and then writes (overlap, `version`, the outbox row) is **one**
  `suspendTransaction`, never several repository calls in a service. A second transaction would wait for the first
  and fail with `database is locked`.
- Exposed re-runs a transaction block when it throws an `SQLException`, so the block must be safe to re-run (generate
  ids inside it, no external side effects).
- Test concurrency on a file database with `FileDatabaseTestSupport` (the same setup as production), not on the
  in-memory database of `TestDbSetup`, which locks differently.

#### Deploy runbook

Releases are GitHub Releases `2.0.0-rc.N` cut from `v2` and go to the test environment first; production stays on `1.6.x` until `v2` is merged to `main`.
For every release:

1. A backup is taken and restorable, and the host clock is NTP-synced (the Pi has no RTC; "2 years ahead" checks, `status`, retry times and audit times use the system clock).
2. The migrations were run on a fresh copy of the production database, and the native image was started against that copy.
3. After the deploy: `/metrics` answers, one authenticated `GET` works, and the Flyway version is as expected:
   `sqlite3 data/app.db "select version from flyway_schema_history order by installed_rank desc limit 1"`.

| Release | Owner's manual steps | Check afterwards | Rollback |
|---|---|---|---|
| 0a | scheduled backup running; keep the old env vars and files | `/metrics`, stats `GET`, check-in/out job logs without errors | previous image |
| 0b | offline copy of `guests.json`; keep the files until stable | guest `GET`, one guest edit, Flyway at 1.6 | previous image **and the restored backup** |
| 1 | none | one guest edit and one check-in/out write leave `updated_*` as designed; Flyway 1.7 | previous image (until 2b) |
| 2a | Cloudflare path routing for the web app; a Cloudflare Access entry for every new guest that must log in | `GET /api/guests` as ADMIN, a stale-version `POST` answers 412 | previous image |
| 2b | none | upload an avatar, check the stored bytes have no EXIF | not below PR 1 |
| 2c | set `BOOTSTRAP_ADMIN_*` only on a fresh database | startup log says nothing was done in production | previous image |
| 4a | none | `GET /api/reservations` with the API key and as a user | previous image |
| 4b + 5 | share the calendar with the service account ("Make changes to events"); test-calendar checklist done; calendar title property | create, move and delete a reservation: the event follows, `calendar_outbox` is empty | backup if API-created reservations matter; else previous image |

Rows 0a and 0b are already deployed. The env vars removed in 0a and 0b (`GUEST_FILE_PATH`, `GUEST_AVATARS_DIRECTORY_PATH`, `GOOGLE_CALENDAR_SYNC_*`,
`GOOGLE_CALENDAR_SUMMARY_TO_GUEST_FILE_PATH`) stay in the deployment config until those releases have proven stable, because the old image fails startup without them.

#### Google Calendar notifications (outbox)

A reservation write never calls Google. In the same transaction it adds a row to `calendar_outbox` (`INSERT`, `UPDATE` or `DELETE`), and a task
(`CalendarOutboxDrain`, every `CALENDAR_OUTBOX_DRAIN_INTERVAL_SECONDS`, around the clock) pushes the rows: one push per reservation, the current times, the event id is the
reservation id, only `start` and `end` are patched. Only pushed rows are deleted, a failed push keeps its row with a growing wait (`attempts`, `last_error` of at most 500
characters, `next_attempt_time`). The Google client is created inside the first push, so a missing or invalid `GOOGLE_CREDENTIALS_FILE_PATH` never stops the app: pushes fail
and retry and the table fills up; at 200 pending rows reservation writes answer `503`.

- **Watch:** `calendar_outbox_pending` and `calendar_outbox_oldest_age_seconds` on `/metrics` (`/metrics` is unauthenticated; the Pi is only reachable through Cloudflare). The app logs
  an ERROR when the oldest row is older than `CALENDAR_OUTBOX_STUCK_THRESHOLD_MINUTES`, and an ERROR for a row that needs attention (a `403` that is not a rate limit, usually the calendar
  is not shared with the service account, or a `401`). Look at the rows:
  `sqlite3 data/app.db "select id, reservation_id, operation, attempts, next_attempt_time, last_error from calendar_outbox order by id"`.
- **Never retried:** a legacy reservation id (starts with `_`, 110 of 139) is never inserted (Google rejects it): logged as ERROR and the row is deleted, so a legacy event that was
  deleted in Google loses its notification. An insert that answers `400`, or `409` after a failed restore, is dropped the same way.
- **After restoring an old backup** Google Calendar is out of step (events for reservations that no longer exist, stale times) and the outbox is gone. Re-enqueue every reservation and
  delete the phantom events by hand in Google Calendar:
  `sqlite3 data/app.db "insert into calendar_outbox (reservation_id, operation, created_time, next_attempt_time) select id, 'UPDATE', strftime('%Y-%m-%dT%H:%M:%fZ','now'), strftime('%Y-%m-%dT%H:%M:%fZ','now') from reservation"`
  (an `UPDATE` of a missing event restores it or creates it). A reconcile command is not built.
- **Test calendar:** use a separate calendar (`GOOGLE_CALENDAR_ID`) until these Google behaviours are checked, they are not documented: insert with the id of a deleted event (`409`?), a patch that sets
  `status = confirmed` on a cancelled event, delete of a missing vs an already deleted event (`404` or `410`), a patch of an all-day event to a timed one, and patch and delete of a legacy `_` id.

#### Logging and privacy

- `logback.xml` only has a console appender, so rotation is the container runtime's job. Set a size-limited Docker logging driver on the host, for example
  `logging: { driver: json-file, options: { max-size: "10m", max-file: "5" } }` in the compose file, so logs cannot fill the SD card.
- Rule: do not log request bodies, emails or birth years; log guest ids. Ktor `CallLogging` is not installed.
- Checked 2026-11-07 (grep of log statements in `src/main`): two statements still log an email address and should be changed to log a guest id or nothing:
  `AppUserService.userByEmail` (ambiguous identity) and the "has no role in the application" warning in `CloudflareAccessAuthentication.kt`.
  Everything else logs ids, counts or ranges. The `summary` in the trace-level reservation repository logs is old calendar text, not personal data of a guest.
- `FAMILY_USER` sees email, role and birth year of every guest through `GET /api/guests` (accepted). The API key is read-only and sent in clear text on the home LAN
  (accepted; rotate it when the Pi moves). `/metrics` is unauthenticated: the Pi is only reachable through Cloudflare.
