package no.slomic.smarthytte.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

abstract class BaseIdTable<T : Any>(name: String = "") : IdTable<T>(name) {
    val createdTime: Column<Instant> = timestamp("created_time")
    val updatedTime: Column<Instant?> = timestamp("updated_time").nullable()
    val version: Column<Short> = short(name = "version").default(defaultValue = 1)
}

abstract class BaseEntity<T : Any>(id: EntityID<T>, table: BaseIdTable<T>) : Entity<T>(id) {
    var createdTime: Instant by table.createdTime
    var updatedTime: Instant? by table.updatedTime
    var version: Short by table.version
}

/** Actor recorded in `created_by`/`updated_by` for changes that no authenticated user made (imports, tasks). */
const val SYSTEM_ACTOR = "system"

/**
 * A [BaseIdTable] with the audit columns `created_by` (required, set on create only) and `updated_by` (set only when an
 * existing row is updated). The value is the authenticated user's id, or [SYSTEM_ACTOR]. It is plain text, not a
 * foreign key, because [SYSTEM_ACTOR] is not a guest.
 */
abstract class AuditedIdTable<T : Any>(name: String = "") : BaseIdTable<T>(name) {
    val createdBy: Column<String> = text("created_by")
    val updatedBy: Column<String?> = text("updated_by").nullable()
}

abstract class AuditedEntity<T : Any>(id: EntityID<T>, table: AuditedIdTable<T>) : BaseEntity<T>(id, table) {
    var createdBy: String by table.createdBy
    var updatedBy: String? by table.updatedBy
}

/** Marks the entity as updated by [actor] now: bumps `version` and sets `updated_time` and `updated_by`. */
fun AuditedEntity<*>.markUpdated(actor: String) {
    version = version.inc()
    updatedTime = Clock.System.now().truncatedToMillis()
    updatedBy = actor
}

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
    transaction { block() }
}
