package no.slomic.smarthytte.common

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

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

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T = newSuspendedTransaction(
    context = Dispatchers.IO,
    statement = block,
)

enum class PersistenceResult {
    ADDED,
    DELETED,
    UPDATED,
    NO_ACTION,
}
