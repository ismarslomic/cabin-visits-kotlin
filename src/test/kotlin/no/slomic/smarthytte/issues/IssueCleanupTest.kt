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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val START = Instant.parse("2026-11-16T10:00:00Z")
private const val ACTOR = "system"
private val APPROVAL_EXPIRY = 15.minutes

class IssueCleanupTest :
    StringSpec({
        val testDbSetup = TestDbSetup()
        val clock = MutableClock(START)
        val repository = SqliteIssueRepository(clock)
        val cleanup = IssueCleanup(repository, APPROVAL_EXPIRY, clock)

        beforeTest {
            testDbSetup.setupDb()
            clock.current = START
        }

        afterTest { testDbSetup.teardownDb() }

        suspend fun record(code: IssueCode, key: String) =
            repository.record(code = code, key = key, message = "m", causeType = null, actor = ACTOR)

        suspend fun resolve(code: IssueCode, key: String) = repository.resolve(code, key, ACTOR)

        suspend fun find(code: IssueCode, key: String): Issue? = repository.findByCodeAndKey(code, key)

        fun markRead(key: String, at: Instant) {
            transaction { IssueTable.update({ IssueTable.issueKey eq key }) { it[readTime] = at } }
        }

        fun rows(): Int = transaction { IssueTable.selectAll().count().toInt() }

        val approval = IssueCode.JLR_APPROVAL_REQUIRED

        "the approval issue is resolved when it was last reported more than 15 minutes ago" {
            record(approval, "JLR_APPROVAL_REQUIRED")
            clock.current = START + APPROVAL_EXPIRY + 1.seconds

            cleanup.cleanOnce()

            val issue = checkNotNull(find(approval, "JLR_APPROVAL_REQUIRED"))
            issue.status shouldBe IssueStatus.RESOLVED
            issue.resolvedTime shouldBe START + APPROVAL_EXPIRY + 1.seconds
            issue.version shouldBe 2
        }

        "the approval issue is kept while the owner can still approve" {
            record(approval, "JLR_APPROVAL_REQUIRED")
            clock.current = START + APPROVAL_EXPIRY

            cleanup.cleanOnce()

            checkNotNull(find(approval, "JLR_APPROVAL_REQUIRED")).status shouldBe IssueStatus.OPEN
        }

        "a new report of the approval issue restarts the 15 minutes" {
            record(approval, "JLR_APPROVAL_REQUIRED")
            clock.current = START + 10.minutes
            record(approval, "JLR_APPROVAL_REQUIRED")
            clock.current = START + 20.minutes

            cleanup.cleanOnce()

            checkNotNull(find(approval, "JLR_APPROVAL_REQUIRED")).status shouldBe IssueStatus.OPEN
        }

        "only the approval issue expires, other open issues stay open however old they are" {
            record(IssueCode.JLR_AUTH_FAILED, "JLR_AUTH_FAILED")
            record(IssueCode.PLACE_MISSING, "PLACE_MISSING:1")
            clock.current = START + 90.days

            cleanup.cleanOnce()

            find(IssueCode.JLR_AUTH_FAILED, "JLR_AUTH_FAILED")?.status shouldBe IssueStatus.OPEN
            find(IssueCode.PLACE_MISSING, "PLACE_MISSING:1")?.status shouldBe IssueStatus.OPEN
            rows() shouldBe 2
        }

        "a resolved issue is deleted 30 days after it was resolved, read or not" {
            val code = IssueCode.PLACE_MISSING
            record(code, "unread")
            record(code, "read")
            resolve(code, "unread")
            resolve(code, "read")
            markRead("read", START - 1.days)

            clock.current = START + RESOLVED_RETENTION - 1.seconds
            cleanup.cleanOnce()
            rows() shouldBe 2

            clock.current = START + RESOLVED_RETENTION + 1.seconds
            cleanup.cleanOnce()
            rows() shouldBe 0
        }

        "a read after the resolve restarts the 30 days" {
            val code = IssueCode.PLACE_MISSING
            record(code, "a")
            resolve(code, "a")
            markRead("a", START + 10.days)

            clock.current = START + RESOLVED_RETENTION + 1.seconds
            cleanup.cleanOnce()
            rows() shouldBe 1

            clock.current = START + 10.days + RESOLVED_RETENTION + 1.seconds
            cleanup.cleanOnce()
            rows() shouldBe 0
        }

        "an open issue is never deleted, read or not" {
            val code = IssueCode.PLACE_MISSING
            record(code, "unread")
            record(code, "read")
            markRead("read", START)
            clock.current = START + 365.days

            cleanup.cleanOnce()

            rows() shouldBe 2
        }

        "a resolved issue that came back is open and is kept" {
            val code = IssueCode.PLACE_MISSING
            record(code, "a")
            resolve(code, "a")
            clock.current = START + 29.days
            record(code, "a")
            clock.current = START + 60.days

            cleanup.cleanOnce()

            checkNotNull(find(code, "a")).status shouldBe IssueStatus.OPEN
            find(code, "a")?.resolvedTime.shouldBeNull()
        }

        "the cleanup does nothing when there is nothing to do" {
            cleanup.cleanOnce()

            rows() shouldBe 0
        }
    })
