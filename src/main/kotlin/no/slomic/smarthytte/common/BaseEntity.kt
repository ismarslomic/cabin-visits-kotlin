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

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
    transaction { block() }
}
