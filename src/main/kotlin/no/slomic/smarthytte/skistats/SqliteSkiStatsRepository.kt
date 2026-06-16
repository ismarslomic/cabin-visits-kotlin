package no.slomic.smarthytte.skistats

class SqliteSkiStatsRepository(
    profileRepository: SkiProfileRepository = SqliteSkiProfileRepository(),
    leaderboardRepository: SkiLeaderboardRepository = SqliteSkiLeaderboardRepository(),
    tokenRepository: SkiTokenRepository = SqliteSkiTokenRepository(),
) : SkiStatsRepository,
    SkiProfileRepository by profileRepository,
    SkiLeaderboardRepository by leaderboardRepository,
    SkiTokenRepository by tokenRepository
