# cabin-visits-kotlin

Ktor 3 / Kotlin backend for hytte-besøksstatistikk. Kompileres til GraalVM native image.

## Teknologi-stack

- Kotlin + Ktor 3 (server)
- SQLite via Exposed ORM, skjema via Flyway-migrasjoner
- GraalVM native image (gradle task `nativeCompile`)
- Detekt for statisk analyse
- Kotest (ShouldSpec) for tester
- Kjør før commit: `./gradlew formatKotlin lintKotlinMain detekt test`

## Pakkestruktur

```
reservations/       Reservation-domenemodell og repository
vehicletrips/       VehicleTrip-domenemodell og repository
statistics/
  model/            Rene dataklasser for API-respons (YearStats, MonthStats, ...)
  calculator/       Rene beregningsfunksjoner (ingen side-effekter, ingen I/O)
  StatsService.kt   Orkestrerer kalkulatorer, henter data fra repositories
  StatsRoutes.kt    Ktor-ruter
common/             Hjelpefunksjoner (InstantUtils, LocalDateUtils, ...)
guests/             Gjeste-domenemodell, repository og GuestProfileService (hvem kan endre hva)
auth/               Cloudflare Access-autentisering, roller (AppRole), UserPrincipal, /api/me
```

## Les DEVELOPER.md først

Før du endrer API, auth, OpenAPI, migrasjoner eller GraalVM-oppsett: les `DEVELOPER.md`. Kommandoer og rutiner står der.

## OpenAPI

- `openapi/cabin-visits.json` genereres fra koden og skal aldri redigeres for hånd. `OpenApiSpecTest` feiler hvis den er utdatert.
- Regenerer med `./gradlew clean` og deretter `UPDATE_OPENAPI=true ./gradlew test --tests '*OpenApiSpecTest' --rerun`. Uten `clean` kan en inkrementell bygging gi en foreldet spec (f.eks. manglende request body).

## GraalVM og databasemigrasjoner

- Nye `@Serializable` request-/respons-klasser trenger tre oppføringer i `META-INF/native-image/main/reachability-metadata.json` (klassen med `Companion`, `$$serializer`, `$Companion.serializer`), se `GuestUpdate` og `MeUpdate`.
- Flyway-migrasjoner (`src/main/resources/db/migration`) endres aldri etter at de er laget. Lag en ny `V1.x`. SQLite kan ikke gi nytt navn til en constraint.
- Test migrasjoner mot en kopi av `data/app.db`, aldri mot selve filen.
- Navn som lagres i data (f.eks. enum-verdier som `AppRole` og `Gender`) kan ikke gis nytt navn uten datamigrasjon.

## API- og gjestekonvensjoner

- API-prinsipper (opprett med `POST`, oppdater med `PUT` som full erstatning, `version` med 412/400, feilformat `ApiError`, OpenAPI, bodygrenser og `415`) står i «API design principles» i `DEVELOPER.md`. Les dem før du lager eller endrer en rute. Skrive-ruter avviker kun for avatar-opplasting (rå `image/jpeg`, ingen `version`).
- Rollen heter `role` overalt (JSON, Kotlin, DB). Typen heter fortsatt `AppRole`.
- En e-post tilhører én gjest (unik indeks `uq_guest_email`, case-insensitiv).
- `ignoreUnknownKeys` er på globalt, så ukjente felt i request body ignoreres.
- `null` for `email` og `role` i `PUT /api/guests/{guestId}` fjerner verdien; felt som ikke kan skrives ignoreres.
- `PUT /api/me` erstatter kun `firstName`, `lastName`, `birthYear` og `gender`. E-post, `isFamily` og `role` endres kun av admin via `PUT /api/guests/{guestId}`.
- Alle gjesteruter svarer feil som JSON `ApiError(code, message)`. `GET /api/me` og `PUT /api/me` svarer med `MeResponse` (inkl. `avatarUrl`).
- `configureApiRoutes` tar en `ApiDependencies`; nye tjenester legges til der.
- **Krav:** hver property i en request- eller respons-klasse og hver path-/query-parameter skal ha både `description` og `example` i OpenAPI (`@JsonSchema.Description` / `@JsonSchema.Example` på propertyen, `description` / `example` på parameteren i `describe {}`). `@JsonSchema.Example` er en JSON-literal: en streng trenger egne anførselstegn. `OpenApiSpecTest` feiler hvis en beskrivelse mangler, og for rutene under `/api/reservations` også et eksempel. Detaljer i prinsipp 10 og 11 i `DEVELOPER.md`.
- Endrer du auth- eller gjesteatferd, oppdater `docs/authentication.md`.

## Testdekning (Codecov)

`codecov.yml` styrer sjekkene på PR. `codecov/patch` (endrede linjer, minst 80 %, 5 % slingringsmonn) skal være grønn. `codecov/project` (ikke lavere enn `main`, 1 % slingringsmonn) viser trenden.

- Ny logikk i `guests/` og `auth/` får test for hver gren, også feil- og konfliktstiene. Rute-tester dekker ikke service-grener alene.
- En fil du endrer uten tester får en testklasse. Gjør konstruktøren testbar ved å sende inn properties med standardverdi, som `VehicleTripService` gjør.
- Sjekk lokalt før push: `./gradlew test` skriver `build/reports/jacoco/test/jacocoTestReport.xml`. Linjer med `mi` > 0 eller `mb` > 0 i filene du endret mangler dekning. Dekk de som har logikk. Utilgjengelige forsvarsgrener (`error(...)`) og deklarative `describe{}`-blokker kan stå, men ikke senk dekningen for å slippe unna.
- Hver statuskode som OpenAPI-spesifikasjonen dokumenterer for en rute skal ha en test: `ResponseCodeAudit` feiler hele `./gradlew test` ellers. Konfigurer test-apper med `configureTestApiRoutes` (eller `recordResponses()`), og fjern en dokumentert kode som ikke kan oppstå i stedet for å la den stå.
- Når Codecov feiler: åpne PR-kommentaren, se tabellen «Files with missing lines» og skriv tester for linjene som mangler. Ikke slett eller svekk tester, og ikke senk terskler i `codecov.yml` for å få en PR gjennom. Spør brukeren først.

## Viktige domenekonvensjoner

### Tidssoner og datoer

- Alle `Instant`-felter i domenemodellen (`Reservation.startTime`, `VehicleTrip.startTime` osv.) lagres og behandles som **UTC**.
- Konvertering til Oslo-tidssone skjer **kun** i service-laget (`StatsService`) når det trengs for å beregne kalendergrenser (f.eks. første dag i et år/måned).
- Kalkulatorer i `statistics/calculator/` mottar alltid `fromDate: LocalDate` og `toDateExclusive: LocalDate` — de gjør **ingen** tidszone-konvertering internt.
- `Instant.toUtcDate()` og `Instant.toOsloDate()` finnes i `common/InstantUtils.kt`.
- `VehicleTrip.startDate` bruker `toUtcDate()` fordi `startTime` er konstruert med Oslo-tidssone i `VehicleTripResponse.kt` — resultatet er ekvivalent.
- `Reservation.startTime` og `endTime` er instants. Eksisterende rader kommer fra den fjernede Google Calendar-synkroniseringen (heldagshendelser har 18:00/15:00 Oslo-tid); nye reservasjoner skal komme via REST API-et (se `.claude/plans/reservations-rest-api.md`).

### LocalDate-grenser

- År-grenser beregnes med `firstDayOfYear(year)` / `firstDayOfYearAfter(year)` fra `LocalDateUtils.kt`.
- Måned-grenser beregnes via `MonthDates(year, month)` i `VisitStatsCalculator.kt` — gir `firstOfMonth` og `firstOfNextMonth`.
- Periodesjekk: `LocalDate.inPeriod(fromDate, toDateExclusive)` = `this in fromDate..<toDateExclusive`.

### Reservation-hjelpere (computed properties)

- `startDate` / `endDate`: `Instant` → `LocalDate` via `toUtcDate()`
- `toCabinDrivingDepartureDate` / `fromCabinDrivingDepartureDate`: `LocalDate` fra første VehicleTrip i henholdsvis `toCabinVehicleTrips` / `fromCabinVehicleTrips`
- `toCabinDrivingDistanceKm`, `fromCabinDrivingDistanceKm`: sum av km for alle turer i retningen

## Detekt-regler å kjenne til

- `MaxLineLength`: trigges lett i modell-filer med lange `@SerialName`-annotasjoner → supprimeres med `@file:Suppress("MaxLineLength")`
- `TooManyFunctions`: trigges i `DrivingStatsCalculator.kt` og `InstantUtils.kt` → supprimeres med `@file:Suppress("TooManyFunctions")`. I en klasse (f.eks. `GuestProfileService`): flytt rene hjelpefunksjoner til private extension-funksjoner på toppnivå i stedet for å supprimere.
- `ReturnCount`: maks 2 return per funksjon. Skriv om med `?:` og `?.let`.
- `CyclomaticComplexMethod`: grense ~15. Løses ved å trekke ut private hjelpemetoder.
- `LongParameterList`: trigges i testhjelpere → supprimeres med `@Suppress("LongParameterList")` på funksjonen.

## Kalkulatormønster

Kalkulatorer er rene top-level funksjoner (ikke klasser). Eksempel:

```kotlin
fun calculateYearDrivingTimeStats(
    fromDate: LocalDate,
    toDateExclusive: LocalDate,
    reservations: List<Reservation>,
): YearDrivingTimeStats
```

`year` og `month` utledes fra `fromDate` inne i funksjonen — de sendes **ikke** inn som parametere.

## Statistikkmodell-konvensjoner

- Separate typer for år og måned: `YearDrivingTimeStats` / `MonthDrivingTimeStats` (ikke slått sammen).
- Månedstyper inneholder `comparedToPrevMonth`-felter for delta mot forrige måned.
- Felt med formatert visning har både råverdi (`...Minutes: Int?`) og formatert streng (`...: String?`).
- Modell-filer i `statistics/model/` er rene dataklasser med `@Serializable` og `@SerialName`.