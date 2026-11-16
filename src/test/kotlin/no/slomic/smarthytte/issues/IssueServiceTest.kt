package no.slomic.smarthytte.issues

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Instant

private val ISSUE = Issue(
    id = 1,
    code = IssueCode.PLACE_MISSING,
    key = "PLACE_MISSING:1",
    status = IssueStatus.OPEN,
    message = "no city",
    causeType = null,
    occurrenceCount = 1,
    firstOccurredTime = Instant.parse("2026-11-16T10:00:00Z"),
    lastOccurredTime = Instant.parse("2026-11-16T10:00:00Z"),
    resolvedTime = null,
    readTime = null,
    version = 1,
)

/** Remembers the filters it was asked for and answers [outcome] and [stored], the rest is not used here. */
private class AdminFakeIssueRepository(var outcome: IssueReadOutcome, var stored: Issue?) : IssueRepository {
    var listed: Triple<Boolean?, IssueStatus?, Int>? = null

    override suspend fun list(isRead: Boolean?, status: IssueStatus?, limit: Int): List<Issue> {
        listed = Triple(isRead, status, limit)
        return listOf(ISSUE)
    }

    override suspend fun setRead(id: Long, expectedVersion: Short, read: Boolean, actor: String) = outcome

    override suspend fun findById(id: Long): Issue? = stored

    override suspend fun record(code: IssueCode, key: String, message: String, causeType: String?, actor: String) =
        error("not used")

    override suspend fun resolve(code: IssueCode, key: String, actor: String): Boolean = error("not used")

    override suspend fun findByCodeAndKey(code: IssueCode, key: String): Issue = error("not used")

    override suspend fun unreadCount(): Int = 7

    override suspend fun resolveOpenBefore(code: IssueCode, occurredBefore: Instant, actor: String): Int =
        error("not used")

    override suspend fun deleteResolvedBefore(cutoff: Instant): Int = error("not used")
}

class IssueServiceTest :
    StringSpec({
        val repository = AdminFakeIssueRepository(IssueReadOutcome.Updated, ISSUE)
        val service = IssueService(repository)

        "list should pass the parsed filters to the repository and default the limit to 100" {
            service.list("true", "RESOLVED", "5").shouldBeInstanceOf<IssueListResult.Listed>()
            repository.listed shouldBe Triple(true, IssueStatus.RESOLVED, 5)

            service.list(null, null, null)
            repository.listed shouldBe Triple(null, null, DEFAULT_ISSUE_LIMIT)
        }

        "list should accept the limits 1 and 500 and reject the values outside them" {
            service.list(null, null, "1").shouldBeInstanceOf<IssueListResult.Listed>()
            service.list(null, null, "500").shouldBeInstanceOf<IssueListResult.Listed>()
            listOf("0", "501", "-1", "1.5", "").forEach {
                service.list(null, null, it).shouldBeInstanceOf<IssueListResult.Invalid>()
            }
        }

        "list should reject a read or status that is not exactly true, false, OPEN or RESOLVED" {
            listOf("TRUE", "yes", "").forEach {
                service.list(it, null, null).shouldBeInstanceOf<IssueListResult.Invalid>()
            }
            listOf("open", "DONE", "").forEach {
                service.list(null, it, null).shouldBeInstanceOf<IssueListResult.Invalid>()
            }
        }

        "an invalid filter should name the parameter" {
            service.list("x", null, null).shouldBeInstanceOf<IssueListResult.Invalid>().message shouldBe
                "read must be true or false"
            service.list(null, "x", null).shouldBeInstanceOf<IssueListResult.Invalid>().message shouldBe
                "status must be OPEN or RESOLVED"
            service.list(null, null, "x").shouldBeInstanceOf<IssueListResult.Invalid>().message shouldBe
                "limit must be a number from 1 to 500"
        }

        "mark read should answer the stored issue after an update or an unchanged request" {
            listOf(IssueReadOutcome.Updated, IssueReadOutcome.Unchanged).forEach {
                repository.outcome = it
                service.markRead("admin", 1, IssueUpdate(1, true)) shouldBe IssueUpdateResult.Updated(ISSUE)
            }
        }

        "mark read should answer not found when the issue is gone after the update, and map the other outcomes" {
            repository.outcome = IssueReadOutcome.Updated
            repository.stored = null
            service.markRead("admin", 1, IssueUpdate(1, true)) shouldBe IssueUpdateResult.NotFound

            repository.outcome = IssueReadOutcome.NotFound
            service.markRead("admin", 1, IssueUpdate(1, true)) shouldBe IssueUpdateResult.NotFound

            repository.outcome = IssueReadOutcome.VersionMismatch
            service.markRead("admin", 1, IssueUpdate(1, true)) shouldBe IssueUpdateResult.VersionMismatch
        }

        "unread count should come from the repository" {
            service.unreadCount() shouldBe 7
        }
    })
