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
`POST /api/guests/{guestId}`. The role is read from the database on every request, so a changed role applies without
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

`POST /api/me` (every role, own guest, only firstName, lastName, birthYear and gender) calls
`GuestProfileService.updateOwnProfile(user, update)`, and `POST /api/guests/{guestId}` (`authorized(AppRole.ADMIN)`)
calls `GuestProfileService.update(actor, guestId, update)`. The service decides what may be changed and validates the
values. The route only maps the result to a status. Do not add such rules to the routes, see
[docs/authentication.md](docs/authentication.md#changing-guest-data).

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

#### Logging and privacy

- `logback.xml` only has a console appender, so rotation is the container runtime's job. Set a size-limited Docker logging driver on the host, for example
  `logging: { driver: json-file, options: { max-size: "10m", max-file: "5" } }` in the compose file, so logs cannot fill the SD card.
- Rule: do not log request bodies, emails or birth years; log guest ids. Ktor `CallLogging` is not installed.
- Checked 2026-11-07 (grep of log statements in `src/main`): two statements still log an email address and should be changed to log a guest id or nothing:
  `AppUserService.userByEmail` (ambiguous identity) and the "has no role in the application" warning in `CloudflareAccessAuthentication.kt`.
  Everything else logs ids, counts or ranges. The `summary` in the trace-level reservation repository logs is old calendar text, not personal data of a guest.
- `FAMILY_USER` sees email, role and birth year of every guest through `GET /api/guests` (accepted). The API key is read-only and sent in clear text on the home LAN
  (accepted; rotate it when the Pi moves). `/metrics` is unauthenticated: the Pi is only reachable through Cloudflare.
