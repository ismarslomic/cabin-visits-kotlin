package no.slomic.smarthytte.issues

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.utils.TestDbSetup
import no.slomic.smarthytte.vehicletrips.jlr.MutableClock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
            repository.findById(1).shouldBeNull()
        }

        "list: sort by last occurrence and then id, both descending, and apply the limit" {
            record(key = "a")
            record(key = "b")
            clock.current = START + 5.minutes
            record(key = "c")
            record(key = "a")

            // c and a occurred at the same time, so the higher id comes first
            repository.list(null, null, 10).map { it.key } shouldBe listOf("c", "a", "b")
            repository.list(null, null, 2).map { it.key } shouldBe listOf("c", "a")
        }

        "list: sort occurrences that differ by milliseconds within the same second" {
            record(key = "first")
            clock.current = START + 500.milliseconds
            record(key = "second")
            clock.current = START + 1.seconds
            record(key = "third")
            clock.current = START + 1500.milliseconds
            record(key = "fourth")

            repository.list(null, null, 10).map { it.key } shouldBe listOf("fourth", "third", "second", "first")
        }

        "list: filter on read state and status, and combine the filters" {
            record(key = "unread")
            record(key = "read")
            record(key = "resolved")
            val read = checkNotNull(find("read")).id
            repository.setRead(read, 1, true, ACTOR)
            repository.resolve(CODE, "resolved", ACTOR)

            repository.list(true, null, 10).map { it.key } shouldBe listOf("read")
            repository.list(false, null, 10).map { it.key }.toSet() shouldBe setOf("unread", "resolved")
            repository.list(null, IssueStatus.RESOLVED, 10).map { it.key } shouldBe listOf("resolved")
            repository.list(false, IssueStatus.OPEN, 10).map { it.key } shouldBe listOf("unread")
            repository.list(true, IssueStatus.RESOLVED, 10) shouldBe emptyList()
        }

        "unread count: count only open issues that are not read" {
            record(key = "unread")
            record(key = "read")
            record(key = "resolved")
            repository.setRead(checkNotNull(find("read")).id, 1, true, ACTOR)
            repository.resolve(CODE, "resolved", ACTOR)

            repository.unreadCount() shouldBe 1
        }

        "set read: mark read, bump the version and write the actor" {
            record()
            val id = checkNotNull(find()).id
            clock.current = START + 1.minutes

            repository.setRead(id, 1, true, "admin") shouldBe IssueReadOutcome.Updated

            val issue = checkNotNull(repository.findById(id))
            issue.readTime shouldBe START + 1.minutes
            issue.version shouldBe 2
            transaction { IssueTable.selectAll().single()[IssueTable.updatedBy] } shouldBe "admin"
        }

        "set read: mark unread again" {
            record()
            val id = checkNotNull(find()).id
            repository.setRead(id, 1, true, ACTOR)

            repository.setRead(id, 2, false, ACTOR) shouldBe IssueReadOutcome.Updated

            val issue = checkNotNull(repository.findById(id))
            issue.readTime.shouldBeNull()
            issue.version shouldBe 3
        }

        "set read: a request for the state the issue has changes nothing and keeps the version" {
            record()
            val id = checkNotNull(find()).id

            repository.setRead(id, 1, false, ACTOR) shouldBe IssueReadOutcome.Unchanged
            repository.setRead(id, 1, true, ACTOR) shouldBe IssueReadOutcome.Updated
            repository.setRead(id, 2, true, ACTOR) shouldBe IssueReadOutcome.Unchanged

            checkNotNull(repository.findById(id)).version shouldBe 2
        }

        "set read: reject an outdated version and an unknown issue" {
            record()
            val id = checkNotNull(find()).id

            repository.setRead(id, 7, true, ACTOR) shouldBe IssueReadOutcome.VersionMismatch
            repository.setRead(id + 1, 1, true, ACTOR) shouldBe IssueReadOutcome.NotFound

            checkNotNull(repository.findById(id)).readTime.shouldBeNull()
        }

        "set read: a resolved issue can be marked read" {
            record()
            repository.resolve(CODE, KEY, ACTOR)
            val issue = checkNotNull(find())

            repository.setRead(issue.id, issue.version, true, ACTOR) shouldBe IssueReadOutcome.Updated

            checkNotNull(repository.findById(issue.id)).status shouldBe IssueStatus.RESOLVED
        }

        "resolve open before: resolve only open issues of the code that were last reported before the time" {
            val approval = IssueCode.JLR_APPROVAL_REQUIRED
            repository.record(approval, "old", "m", null, ACTOR)
            repository.record(approval, "done", "m", null, ACTOR)
            repository.resolve(approval, "done", ACTOR)
            repository.record(IssueCode.JLR_AUTH_FAILED, "other", "m", null, ACTOR)
            clock.current = START + 10.minutes
            repository.record(approval, "recent", "m", null, ACTOR)
            val cutoff = START + 5.minutes

            repository.resolveOpenBefore(approval, cutoff, ACTOR) shouldBe 1

            repository.findByCodeAndKey(approval, "old")?.status shouldBe IssueStatus.RESOLVED
            repository.findByCodeAndKey(approval, "old")?.version shouldBe 2
            repository.findByCodeAndKey(approval, "done")?.version shouldBe 2
            repository.findByCodeAndKey(approval, "recent")?.status shouldBe IssueStatus.OPEN
            repository.findByCodeAndKey(IssueCode.JLR_AUTH_FAILED, "other")?.status shouldBe IssueStatus.OPEN
            repository.resolveOpenBefore(approval, cutoff, ACTOR) shouldBe 0
        }

        "resolve open before: keep an issue that was last reported exactly at the time" {
            record()

            repository.resolveOpenBefore(CODE, START, ACTOR) shouldBe 0
            repository.resolveOpenBefore(CODE, START + 1.milliseconds, ACTOR) shouldBe 1
        }

        "delete resolved before: delete only resolved issues whose resolve and read time are before the time" {
            record(key = "open")
            record(key = "resolved")
            record(key = "read later")
            repository.resolve(CODE, "resolved", ACTOR)
            repository.resolve(CODE, "read later", ACTOR)
            transaction {
                IssueTable.update({ IssueTable.issueKey eq "read later" }) { it[readTime] = START + 1.hours }
            }

            // "resolved" was resolved at START and never read, "read later" was read an hour after the resolve
            repository.deleteResolvedBefore(START + 1.minutes) shouldBe 1
            repository.deleteResolvedBefore(START + 1.hours) shouldBe 0
            repository.deleteResolvedBefore(START + 1.hours + 1.milliseconds) shouldBe 1
            find("open").shouldNotBeNullIssue()
            find("resolved").shouldBeNull()
            find("read later").shouldBeNull()
        }

        "delete resolved before: an unread issue resolved exactly at the time is kept" {
            record()
            repository.resolve(CODE, KEY, ACTOR)

            repository.deleteResolvedBefore(START) shouldBe 0
            repository.deleteResolvedBefore(START + 1.milliseconds) shouldBe 1
        }
    })

private fun Issue?.shouldNotBeNullIssue(): Issue = checkNotNull(this) { "Expected an issue, got null" }
