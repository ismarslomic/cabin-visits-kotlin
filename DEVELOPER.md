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

### Authorization and restricting endpoints by role

Setup of Cloudflare Access, the status codes and how roles are granted are described in
[docs/authentication.md](docs/authentication.md). This section is about the code.

#### How a request is authorized

Authentication (who is calling) and authorization (what may they do) are separate steps:

1. **Authentication** (`CloudflareAccessAuthentication.kt`): the Ktor `jwt` provider `cloudflare-access` verifies the
   HTTP header `Cf-Access-Jwt-Assertion` against Cloudflare's signing keys (signature, issuer, audience, expiry). The `email`
   claim is then looked up with `AppUserService.userByEmail` which matches the `guest` row that has an `app_role` through `GuestRepository.guestsByEmail`.
   The result is a `UserPrincipal` (id, name, email, role).
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

The roles (`AppRole`) are `ADMIN`, `FAMILY_USER` and `GUEST_USER`. They are stored in `guest.app_role` and set from the
`appRole` property in `data/guests.json` at startup, so the file is the source of truth. The role is read from the
database on every request, so a changed role applies without the user logging in again.

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

`POST /api/me` (every role, own guest) and `POST /api/guests/{guestId}` (`authorized(AppRole.ADMIN)`) both call
`GuestProfileService.update(actor, guestId, update)`, which decides what the actor may change and validates the values.
The route only maps the result to a status. Do not add such rules to the routes. Properties changed this way are stored
in `guest.api_edited_fields` and skipped by the `guests.json` sync, see
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
