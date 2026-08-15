package no.slomic.smarthytte.skistats

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

fun createSkiProfile(
    id: String = "ABCD12345",
    name: String = "Peter Doe",
    profileImageUrl: String? = "https://localhost/profile-images/peter.jpg?width=200",
    isPrivate: Boolean = false,
): SkiProfile = SkiProfile(
    id = id,
    name = name,
    profileImageUrl = profileImageUrl,
    isPrivate = isPrivate,
)

// Mirrors SkiLeaderboardPeriod fields — all have defaults, callers set only what they need
@Suppress("LongParameterList")
fun createSkiLeaderboardPeriod(
    type: PeriodType = PeriodType.DAY,
    value: String = "2026-02-15",
    startDate: LocalDate = LocalDate(2026, 2, 15),
    weekId: String? = "2907",
    seasonId: String = "29",
    seasonName: String = "Season 29",
    year: Int? = 2026,
    weekNumber: Int? = 7,
): SkiLeaderboardPeriod = SkiLeaderboardPeriod(
    type = type,
    value = value,
    startDate = startDate,
    weekId = weekId,
    seasonId = seasonId,
    seasonName = seasonName,
    year = year,
    weekNumber = weekNumber,
)

// Mirrors SkiLeaderboardEntry fields — all have defaults, callers set only what they need
@Suppress("LongParameterList")
fun createSkiLeaderboardEntry(
    profileId: String = "ismar",
    period: SkiLeaderboardPeriod = createSkiLeaderboardPeriod(),
    entryUserId: String = "ABCD12345",
    position: Int = 1,
    dropHeightInMeter: Int = 4811,
    leaderboardUpdatedAtUtc: Instant = Instant.parse("2026-02-15T18:15:07Z"),
): SkiLeaderboardEntry = SkiLeaderboardEntry(
    id = leaderboardEntryId(profileId, period, entryUserId),
    profileId = profileId,
    period = period,
    leaderboardUpdatedAtUtc = leaderboardUpdatedAtUtc,
    entryUserId = entryUserId,
    position = position,
    dropHeightInMeter = dropHeightInMeter,
)
