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

// Mirrors SkiLeaderboardEntry fields — all have defaults, callers set only what they need
@Suppress("LongParameterList")
fun createSkiLeaderboardEntry(
    profileId: String = "ismar",
    periodType: PeriodType = PeriodType.DAY,
    periodValue: String = "2026-02-15",
    entryUserId: String = "ABCD12345",
    position: Int = 1,
    dropHeightInMeter: Int = 4811,
    startDate: LocalDate = LocalDate(2026, 2, 15),
    weekId: String? = "2907",
    seasonId: String = "29",
    leaderboardUpdatedAtUtc: Instant = Instant.parse("2026-02-15T18:15:07Z"),
): SkiLeaderboardEntry = SkiLeaderboardEntry(
    id = leaderboardEntryId(profileId, periodType, periodValue, entryUserId),
    profileId = profileId,
    periodType = periodType,
    periodValue = periodValue,
    startDate = startDate,
    weekId = weekId,
    seasonId = seasonId,
    leaderboardUpdatedAtUtc = leaderboardUpdatedAtUtc,
    entryUserId = entryUserId,
    position = position,
    dropHeightInMeter = dropHeightInMeter,
)
