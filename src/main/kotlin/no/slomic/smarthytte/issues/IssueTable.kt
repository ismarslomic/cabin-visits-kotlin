package no.slomic.smarthytte.issues

import no.slomic.smarthytte.common.AuditedIdTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

/**
 * One row per [code] and [issueKey]. The status and `readTime` are separate: the code resolves an issue, the admin
 * reads it. `version` changes only when the status or `readTime` changes, not when a problem repeats.
 */
object IssueTable : AuditedIdTable<Long>(name = "issue") {
    override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
    val code: Column<IssueCode> = enumerationByName("code", length = 50, IssueCode::class)
    val issueKey: Column<String> = varchar("issue_key", length = 255)
    val status: Column<IssueStatus> = enumerationByName("status", length = 10, IssueStatus::class)
    val message: Column<String> = text("message")
    val causeType: Column<String?> = varchar("cause_type", length = 255).nullable()
    val occurrenceCount: Column<Int> = integer("occurrence_count")
    val firstOccurredTime: Column<Instant> = timestamp("first_occurred_time")
    val lastOccurredTime: Column<Instant> = timestamp("last_occurred_time")
    val resolvedTime: Column<Instant?> = timestamp("resolved_time").nullable()
    val readTime: Column<Instant?> = timestamp("read_time").nullable()

    override val primaryKey = PrimaryKey(firstColumn = id, name = "pk_issue_id")
}
