# cabin-visits-kotlin

Ktor 3 / Kotlin backend for hytte-besøksstatistikk. Kompileres til GraalVM native image.

## Teknologi-stack

- Kotlin + Ktor 3 (server)
- SQLite via Exposed ORM
- GraalVM native image (gradle task `nativeCompile`)
- Detekt for statisk analyse
- Kotest (ShouldSpec) for tester
- Kjør detekt og tester: `./gradlew detekt test`

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
guests/             Gjeste-domenemodell og repository
```

## Viktige domenekonvensjoner

### Tidssoner og datoer

- Alle `Instant`-felter i domenemodellen (`Reservation.startTime`, `VehicleTrip.startTime` osv.) lagres og behandles som **UTC**.
- Konvertering til Oslo-tidssone skjer **kun** i service-laget (`StatsService`) når det trengs for å beregne kalendergrenser (f.eks. første dag i et år/måned).
- Kalkulatorer i `statistics/calculator/` mottar alltid `fromDate: LocalDate` og `toDateExclusive: LocalDate` — de gjør **ingen** tidszone-konvertering internt.
- `Instant.toUtcDate()` og `Instant.toOsloDate()` finnes i `common/InstantUtils.kt`.
- `VehicleTrip.startDate` bruker `toUtcDate()` fordi `startTime` er konstruert med Oslo-tidssone i `VehicleTripResponse.kt` — resultatet er ekvivalent.
- `Reservation.startTime` settes normalt fra Google Calendar RFC3339-tidspunkt. For heldagshendelser settes klokkeslett til 18:00 Oslo-tid.

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
- `TooManyFunctions`: trigges i `DrivingStatsCalculator.kt` og `InstantUtils.kt` → supprimeres med `@file:Suppress("TooManyFunctions")`
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