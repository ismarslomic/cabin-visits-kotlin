package no.slomic.smarthytte.checkin

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object CheckInTable : BaseIdTable<String>(name = "check_in") {
    override val id: Column<EntityID<String>> = varchar("id", length = 30).entityId()
    val timestamp: Column<Instant> = timestamp(name = "timestamp")
    val status: Column<CheckInStatus> = enumerationByName("status", length = 15, CheckInStatus::class)
    override val primaryKey = PrimaryKey(id, name = "pk_check_in_id")
}

class CheckInEntity(id: EntityID<String>) : BaseEntity<String>(id, CheckInTable) {
    companion object : EntityClass<String, CheckInEntity>(CheckInTable)

    var timestamp: Instant by CheckInTable.timestamp
    var status: CheckInStatus by CheckInTable.status
}

fun daoToModel(dao: CheckInEntity) = CheckIn(
    id = dao.id.value,
    timestamp = dao.timestamp,
    status = dao.status,
)

object CheckInSyncTable : IntIdTable(name = "check_in_sync") {
    val latestTimestamp: Column<Instant> = timestamp(name = "latest_timestamp")
    val updated: Column<Instant> = timestamp("updated")
}

class CheckInSyncEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CheckInSyncEntity>(CheckInSyncTable)

    var latestTimestamp: Instant by CheckInSyncTable.latestTimestamp
    var updated: Instant by CheckInSyncTable.updated
}
