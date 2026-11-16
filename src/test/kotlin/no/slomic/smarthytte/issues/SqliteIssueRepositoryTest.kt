package no.slomic.smarthytte.issues

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.jlr.MutableClock
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val START = Instant.parse("2026-11-16T10:00:00Z")
private const val ACTOR = "system"
private val CODE = IssueCode.PLACE_MISSING
private const val KEY = "PLACE_MISSING:1"

class SqliteIssueRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()
        val clock = MutableClock(START)
        val repository = SqliteIssueRepository(clock)

        beforeTest {
            testDbSetup.setupDb()
            clock.current = START
        }

        afterTest { testDbSetup.teardownDb() }

        suspend fun record(message: String = "no city", causeType: String? = null, key: String = KEY) =
            repository.record(code = CODE, key = key, message = message, causeType = causeType, actor = ACTOR)

        suspend fun find(key: String = KEY): Issue? = repository.findByCodeAndKey(CODE, key)

        fun markRead(at: Instant) {
            transaction { IssueTable.update { it[readTime] = at } }
        }

        "record a new problem: insert it as an open, unread issue with count 1" {
            record(message = "no start city", causeType = "PlaceLookupException")

            val issue = find().shouldNotBeNullIssue()
            issue.code shouldBe CODE
            issue.key shouldBe KEY
            issue.status shouldBe IssueStatus.OPEN
            issue.message shouldBe "no start city"
            issue.causeType shouldBe "PlaceLookupException"
            issue.occurrenceCount shouldBe 1
            issue.firstOccurredTime shouldBe START
            issue.lastOccurredTime shouldBe START
            issue.resolvedTime.shouldBeNull()
            issue.readTime.shouldBeNull()
            issue.version shouldBe 1
            issue.severity shouldBe IssueSeverity.WARN
        }

        "record a new problem: keep issues with the same code and different keys apart" {
            record(key = "PLACE_MISSING:1")
            record(key = "PLACE_MISSING:2")

            transaction { IssueTable.selectAll().count() } shouldBe 2
        }

        "record a new problem: write the actor as created_by and leave updated_by empty" {
            record()

            transaction {
                val row = IssueTable.selectAll().single()
                row[IssueTable.createdBy] shouldBe ACTOR
                row[IssueTable.updatedBy].shouldBeNull()
                row[IssueTable.updatedTime].shouldBeNull()
            }
        }

        "record a repeat of an open issue: count it, update message and last time, and keep one row" {
            record(message = "first")
            clock.current = START + 10.minutes

            record(message = "second", causeType = "IllegalStateException")

            val issue = find().shouldNotBeNullIssue()
            issue.occurrenceCount shouldBe 2
            issue.message shouldBe "second"
            issue.causeType shouldBe "IllegalStateException"
            issue.firstOccurredTime shouldBe START
            issue.lastOccurredTime shouldBe START + 10.minutes
            transaction { IssueTable.selectAll().count() } shouldBe 1
        }

        "record a repeat of an open issue: keep readTime and version, so a read issue stays read" {
            record()
            markRead(START + 1.minutes)
            clock.current = START + 10.minutes

            record()

            val issue = find().shouldNotBeNullIssue()
            issue.readTime shouldBe START + 1.minutes
            issue.version shouldBe 1
            issue.status shouldBe IssueStatus.OPEN
        }

        "resolve: set the issue to resolved with the time and bump the version" {
            record()
            clock.current = START + 5.minutes

            repository.resolve(CODE, KEY, ACTOR) shouldBe true

            val issue = find().shouldNotBeNullIssue()
            issue.status shouldBe IssueStatus.RESOLVED
            issue.resolvedTime shouldBe START + 5.minutes
            issue.version shouldBe 2
        }

        "resolve: keep readTime and the row when the issue was read" {
            record()
            markRead(START + 1.minutes)

            repository.resolve(CODE, KEY, ACTOR)

            find().shouldNotBeNullIssue().readTime shouldBe START + 1.minutes
        }

        "resolve: answer false and create no row when there is no issue" {
            repository.resolve(CODE, KEY, ACTOR) shouldBe false

            transaction { IssueTable.selectAll().count() } shouldBe 0
        }

        "resolve: answer false for an issue that is already resolved and leave it alone" {
            record()
            repository.resolve(CODE, KEY, ACTOR)
            val resolved = find()

            repository.resolve(CODE, KEY, ACTOR) shouldBe false

            find() shouldBe resolved
        }

        "resolve: only resolve the issue with the given key" {
            record(key = "PLACE_MISSING:1")
            record(key = "PLACE_MISSING:2")

            repository.resolve(CODE, "PLACE_MISSING:1", ACTOR)

            find("PLACE_MISSING:1").shouldNotBeNullIssue().status shouldBe IssueStatus.RESOLVED
            find("PLACE_MISSING:2").shouldNotBeNullIssue().status shouldBe IssueStatus.OPEN
        }

        "record a repeat of a resolved issue: reopen the row as unread and clear the resolve time" {
            record()
            markRead(START + 1.minutes)
            repository.resolve(CODE, KEY, ACTOR)
            clock.current = START + 20.minutes

            record(message = "again")

            val issue = find().shouldNotBeNullIssue()
            issue.status shouldBe IssueStatus.OPEN
            issue.readTime.shouldBeNull()
            issue.resolvedTime.shouldBeNull()
            issue.occurrenceCount shouldBe 2
            issue.message shouldBe "again"
            issue.lastOccurredTime shouldBe START + 20.minutes
            issue.version shouldBe 3
            transaction { IssueTable.selectAll().count() } shouldBe 1
        }

        "find: return null when the issue does not exist" {
            find().shouldBeNull()
        }
    })

private fun Issue?.shouldNotBeNullIssue(): Issue = checkNotNull(this) { "Expected an issue, got null" }
