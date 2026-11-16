package no.slomic.smarthytte.issues

import no.slomic.smarthytte.common.suspendReadTransaction
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant

class SqliteIssueRepository(private val clock: Clock = Clock.System) : IssueRepository {
    // One write transaction takes the write lock first, so the find and the insert can not race with another writer
    override suspend fun record(code: IssueCode, key: String, message: String, causeType: String?, actor: String) {
        suspendTransaction {
            val now = clock.now().truncatedToMillis()
            val existing = IssueTable.selectAll().where(matches(code, key)).singleOrNull()?.toIssue()
            when {
                existing == null -> insertNew(code, key, message, causeType, actor, now)
                existing.status == IssueStatus.OPEN -> repeatOpen(existing, message, causeType, actor, now)
                else -> reopen(existing, message, causeType, actor, now)
            }
        }
    }

    override suspend fun resolve(code: IssueCode, key: String, actor: String): Boolean = suspendTransaction {
        val now = clock.now().truncatedToMillis()
        val updatedRows = IssueTable.update(where = {
            matches(code, key) and (IssueTable.status eq IssueStatus.OPEN)
        }) {
            it[status] = IssueStatus.RESOLVED
            it[resolvedTime] = now
            it[version] = version + 1.toShort()
            it[updatedTime] = now
            it[updatedBy] = actor
        }
        updatedRows > 0
    }

    override suspend fun findById(id: Long): Issue? = suspendReadTransaction {
        IssueTable.selectAll().where(IssueTable.id eq id).singleOrNull()?.toIssue()
    }

    override suspend fun list(isRead: Boolean?, status: IssueStatus?, limit: Int): List<Issue> =
        suspendReadTransaction {
            val conditions = listOfNotNull(
                isRead?.let { if (it) IssueTable.readTime.isNotNull() else IssueTable.readTime.isNull() },
                status?.let { IssueTable.status eq it },
            )

            IssueTable.selectAll()
                .where(conditions.reduceOrNull { all, next -> all and next } ?: Op.TRUE)
                .orderBy(IssueTable.lastOccurredTime to SortOrder.DESC, IssueTable.id to SortOrder.DESC)
                .limit(limit)
                .map { it.toIssue() }
        }

    override suspend fun unreadCount(): Int = suspendReadTransaction {
        IssueTable.selectAll()
            .where((IssueTable.status eq IssueStatus.OPEN) and IssueTable.readTime.isNull())
            .count()
            .toInt()
    }

    // The find, the version check and the update are one write transaction, so a concurrent change gives a mismatch
    override suspend fun setRead(id: Long, expectedVersion: Short, read: Boolean, actor: String): IssueReadOutcome =
        suspendTransaction {
            val now = clock.now().truncatedToMillis()
            val stored = IssueTable.selectAll().where(IssueTable.id eq id).singleOrNull()?.toIssue()

            when {
                stored == null -> IssueReadOutcome.NotFound

                stored.version != expectedVersion -> IssueReadOutcome.VersionMismatch

                (stored.readTime != null) == read -> IssueReadOutcome.Unchanged

                else -> {
                    IssueTable.update(where = { IssueTable.id eq id }) {
                        it[readTime] = if (read) now else null
                        it[version] = version + 1.toShort()
                        it[updatedTime] = now
                        it[updatedBy] = actor
                    }
                    IssueReadOutcome.Updated
                }
            }
        }

    override suspend fun resolveOpenBefore(code: IssueCode, occurredBefore: Instant, actor: String): Int =
        suspendTransaction {
            val now = clock.now().truncatedToMillis()
            IssueTable.update(where = {
                (IssueTable.code eq code) and
                    (IssueTable.status eq IssueStatus.OPEN) and
                    (IssueTable.lastOccurredTime less occurredBefore.truncatedToMillis())
            }) {
                it[status] = IssueStatus.RESOLVED
                it[resolvedTime] = now
                it[version] = version + 1.toShort()
                it[updatedTime] = now
                it[updatedBy] = actor
            }
        }

    override suspend fun deleteResolvedBefore(cutoff: Instant): Int = suspendTransaction {
        val limit = cutoff.truncatedToMillis()
        IssueTable.deleteWhere {
            (status eq IssueStatus.RESOLVED) and
                (resolvedTime less limit) and
                (readTime.isNull() or (readTime less limit))
        }
    }

    override suspend fun findByCodeAndKey(code: IssueCode, key: String): Issue? = suspendReadTransaction {
        IssueTable.selectAll().where(matches(code, key)).singleOrNull()?.toIssue()
    }
}

private fun matches(code: IssueCode, key: String) = (IssueTable.code eq code) and (IssueTable.issueKey eq key)

private fun insertNew(code: IssueCode, key: String, message: String, causeType: String?, actor: String, now: Instant) {
    IssueTable.insert {
        it[this.code] = code
        it[issueKey] = key
        it[status] = IssueStatus.OPEN
        it[this.message] = message
        it[this.causeType] = causeType
        it[occurrenceCount] = 1
        it[firstOccurredTime] = now
        it[lastOccurredTime] = now
        it[createdTime] = now
        it[createdBy] = actor
    }
}

// The admin sees the same state as before, so `version` stays and a stale "mark read" is not rejected
private fun repeatOpen(existing: Issue, message: String, causeType: String?, actor: String, now: Instant) {
    IssueTable.update(where = { IssueTable.id eq existing.id }) {
        it[this.message] = message
        it[this.causeType] = causeType
        it[occurrenceCount] = occurrenceCount + 1
        it[lastOccurredTime] = now
        it[updatedTime] = now
        it[updatedBy] = actor
    }
}

private fun reopen(existing: Issue, message: String, causeType: String?, actor: String, now: Instant) {
    IssueTable.update(where = { IssueTable.id eq existing.id }) {
        it[status] = IssueStatus.OPEN
        it[resolvedTime] = null
        it[readTime] = null
        it[this.message] = message
        it[this.causeType] = causeType
        it[occurrenceCount] = occurrenceCount + 1
        it[lastOccurredTime] = now
        it[version] = version + 1.toShort()
        it[updatedTime] = now
        it[updatedBy] = actor
    }
}

private fun ResultRow.toIssue() = Issue(
    id = this[IssueTable.id].value,
    code = this[IssueTable.code],
    key = this[IssueTable.issueKey],
    status = this[IssueTable.status],
    message = this[IssueTable.message],
    causeType = this[IssueTable.causeType],
    occurrenceCount = this[IssueTable.occurrenceCount],
    firstOccurredTime = this[IssueTable.firstOccurredTime],
    lastOccurredTime = this[IssueTable.lastOccurredTime],
    resolvedTime = this[IssueTable.resolvedTime],
    readTime = this[IssueTable.readTime],
    version = this[IssueTable.version],
)
