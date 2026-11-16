# Code style

Readability rules that apply to new and changed code. Detekt and the formatter (`./gradlew formatKotlin lintKotlinMain detekt`)
already enforce formatting and complexity limits, so they are not repeated here. Code reviewers (people and agents)
cite the rule number in a finding. Existing code is not rewritten to match, only code that is touched.

| # | Topic | Rule |
|--:|:------|:-----|
| 1 | Conditions | A condition with more than one clause gets a named local val, then the `if` uses the name: `val isAtCabin = distance(trip.start, cabin) <= cabin.radiusMeters`. |
| 2 | Collection chains | Lambdas of 1 to 3 lines stay inline. Longer ones become a named private function used as a function reference: `journeys.filterNot { it.isKnown() }.map(::toVehicleTrip)`. |
| 3 | Mapping | DTOs stay plain data holders. Mapping DTO to domain is a top-level extension function in its own file next to the DTO: `JlrJourneyMapping.kt` with `fun JlrJourneyResponse.toVehicleTrip(...)`. |
| 4 | Naming | External DTOs end in `Response` (`JlrJourneyResponse`). HTTP classes end in `Client`, logic classes in `Service`. |
| 5 | Failures | A function that can fail throws a typed exception. It is caught once, at the boundary in the service that decides what happens, and reported through `IssueReporter`. |
| 6 | Comments | Names first, comment the why. No comment that restates code. KDoc on public classes and non-obvious things. Inline comments give a reason (a measured API quirk, a business rule). |
| 7 | Interfaces | Every repository has an interface with a technology-named implementation (`VehicleTripRepository`, `SqliteVehicleTripRepository`). Services do not need one. Other interfaces only for a real boundary with more than one implementation (`IssueReporter`, `PlaceResolver`). |
| 8 | Branching | Prefer a `when` expression where each branch yields a value: `when (val existing = findExisting(j)) { null -> insertNew(j); else -> update(existing, j) }`. |
| 9 | HTTP clients | Concrete classes taking an `HttpClient`, no interface. Tests pass a Ktor `MockEngine`. |
| 10 | Packages and files | New code goes in sub-packages of the feature package (`vehicletrips/jlr/`, `vehicletrips/places/`), not new top-level packages. One main type per file, small related DTOs may share a file. Cross-cutting helpers live in `common/`. A feature with its own table and API gets its own top-level package, like `issues/` (`IssueReporter`, `IssueRepository`). |
| 11 | Constants and config | Config (`application.yml`, environment variable with `:-default`) for secrets, personal data and values an operator may change. Named `const val` next to the code that uses it for fixed, non-sensitive values (API limits, timeouts, token lifetimes, retry settings). No unnamed magic numbers. |
| 12 | Tests | Fakes at boundaries, `context` per behavior. Hand-written fakes or `MockEngine` for HTTP, `IssueReporter` and the clock, no mocking library. `should` sentences read like requirements. Shared test data lives in one builder file per type with a default for every field, like `VehicleTripMock`. |
| 13 | Time | Inject a `Clock` (constructor parameter, default `Clock.System`). Tests use a fixed clock. |
| 14 | Domain types | Small data classes where they carry meaning (`GeoPoint`, typed config objects). Plain `Double` and `String` for single values. No value classes. |
| 15 | Helper functions | Helpers stay private inside the class. Exception: when Detekt `TooManyFunctions` triggers, move the pure helpers (no state, no I/O) into private top-level extension functions in the same file instead of suppressing the rule. |
| 16 | Arguments | Name the arguments when a call has three or more parameters, or passes a literal Boolean or number: `resolve(point = trip.start, radiusMeters = 300)`. |
| 17 | Function names | `find...` returns null when missing, `get...` throws when missing, `fetch...` calls an external system, `resolve...` computes a decision from inputs. |
| 18 | Logging | Informational logs: one INFO summary line per run with `key=value` facts, DEBUG for per-item detail. Problems go through `IssueReporter`, not the logger. |
