package no.slomic.smarthytte.common

import com.google.api.client.util.DateTime
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class InstantUtilsTest :
    StringSpec({
        "toInstant(DateTime, hour, timeZone, minusDays) should return correct Instant" {
            val timeZone = TimeZone.of("Europe/Oslo")
            val dateTime = DateTime("2025-07-10")
            val hour = 15
            val minusDays = 1

            val expectedInstant = Instant.parse("2025-07-09T13:00:00Z")
            val actualInstant = toInstant(dateTime, hour, timeZone, minusDays)

            actualInstant shouldBe expectedInstant
        }

        "toInstant(DateTime, hour, timeZone) with no minusDays should use given date as-is" {
            val timeZone = TimeZone.of("Europe/Oslo")
            val dateTime = DateTime("2025-07-10")
            val hour = 15

            val expectedInstant = Instant.parse("2025-07-10T13:00:00Z")
            val actualInstant = toInstant(dateTime, hour, timeZone)

            actualInstant shouldBe expectedInstant
        }

        "toInstant(LocalDate, LocalTime, TimeZone) should convert local date and time and zone to correct instant" {
            // Prepare
            val date = LocalDate(year = 2025, month = 7, day = 10)
            val time = LocalTime(hour = 15, minute = 0)
            val timeZone = TimeZone.of("Europe/Oslo")

            // Act
            val instant: Instant = toInstant(date, time, timeZone)

            // Compute the expected instant using java.time for reference
            val javaInstant = ZonedDateTime.of(
                date.year,
                date.month.number,
                date.day,
                time.hour,
                time.minute,
                time.second,
                0,
                ZoneId.of("Europe/Oslo"),
            ).toInstant()

            // Assert
            instant.toJavaInstant() shouldBe javaInstant
        }

        "nowIsoUtcString should return current UTC timestamp in ISO yyyy-MM-dd'T'HH:mm:ssX format" {
            val fixedNowOsloTime = "2024-07-10T14:12:34.423+02:00"

            // Mock Clock.System
            MockKAnnotations.init(this)
            mockkObject(Clock.System)
            every { Clock.System.now() } returns Instant.parse(fixedNowOsloTime)

            val expectedNowUtcTime = "2024-07-10T12:12:34Z"
            val actualNowUtcTime: String = nowIsoUtcString()

            actualNowUtcTime shouldBe expectedNowUtcTime

            // Unmock Clock.System
            unmockkAll()
        }

        "toOsloDate should convert instant in UTC to the correct Oslo LocalDate" {
            // Given: 2025-07-10T22:30:00Z is 2025-07-11 00:30 in Oslo (UTC+2)
            val instant = Instant.parse("2025-07-10T22:30:00Z")

            // When
            val osloDate: LocalDate = instant.toOsloDate()

            // Then
            osloDate shouldBe LocalDate(year = 2025, month = 7, day = 11)
        }

        "osloDateNow should return the current Oslo local date" {
            // When
            val osloNow: LocalDate = osloDateNow()

            // Then: Should match the system's current time as seen in Oslo timezone
            val expected = Clock.System.now()
                .toLocalDateTime(TimeZone.of("Europe/Oslo"))
                .date

            osloNow shouldBe expected
        }
    })
