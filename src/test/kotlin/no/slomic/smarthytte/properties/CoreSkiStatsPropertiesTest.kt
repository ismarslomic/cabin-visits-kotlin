package no.slomic.smarthytte.properties

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CoreSkiStatsPropertiesTest :
    StringSpec({
        val skiProfileId = "ABC123"
        val coreProps = CoreSkiStatsProperties(
            baseUrl = "https://api.example.com",
            authPath = "/oauth/token",
            friendsLeaderboardsPath = "/leaderboards/friends",
            statisticsPeriodsPath = "/users/{skiProfileId}/statistics/periods",
            appInstanceId = "app-instance",
            appPlatform = "ios",
            apiKey = "api-key",
            appVersion = "1.0.0",
            cookie = "cookie",
            userAgent = "user-agent",
        )

        "statisticsPeriodsUrl should replace {skiProfileId} placeholder with the given id" {
            coreProps.statisticsPeriodsUrl(skiProfileId) shouldBe
                "https://api.example.com/users/$skiProfileId/statistics/periods"
        }

        "statisticsPeriodsUrl should produce correct URL with baseUrl having no trailing slash" {
            val props = coreProps.copy(baseUrl = "https://api.example.com")
            props.statisticsPeriodsUrl(skiProfileId) shouldBe
                "https://api.example.com/users/$skiProfileId/statistics/periods"
        }

        "statisticsPeriodsUrl should leave path unchanged when placeholder is absent" {
            val props = coreProps.copy(statisticsPeriodsPath = "/users/statistics/periods")
            props.statisticsPeriodsUrl(skiProfileId) shouldBe
                "https://api.example.com/users/statistics/periods"
        }

        "friendsLeaderboardsUrl should build correct URL for DAY period" {
            coreProps.friendsLeaderboardsUrl("DAY", "2026-02-15") shouldBe
                "https://api.example.com/leaderboards/friends/day/2026-02-15"
        }

        "friendsLeaderboardsUrl should build correct URL for WEEK period" {
            coreProps.friendsLeaderboardsUrl("WEEK", "2907") shouldBe
                "https://api.example.com/leaderboards/friends/week/2907"
        }

        "friendsLeaderboardsUrl should build correct URL for SEASON period" {
            coreProps.friendsLeaderboardsUrl("SEASON", "29") shouldBe
                "https://api.example.com/leaderboards/friends/season/29"
        }

        "friendsLeaderboardsUrl should lowercase the period type" {
            coreProps.friendsLeaderboardsUrl("DAY", "2026-02-15") shouldBe
                coreProps.friendsLeaderboardsUrl("day", "2026-02-15")
        }
    })
