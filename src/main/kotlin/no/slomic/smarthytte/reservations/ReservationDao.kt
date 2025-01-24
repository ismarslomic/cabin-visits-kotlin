package no.slomic.smarthytte.reservations

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import no.slomic.smarthytte.guests.GuestEntity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ReservationTable : BaseIdTable<String>(name = "reservation") {
    override val id: Column<EntityID<String>> = varchar("id", length = 1024).entityId()
    val summary: Column<String?> = varchar(name = "summary", length = 1000).nullable()
    val description: Column<String?> = varchar(name = "description", length = 2000).nullable()
    val startTime: Column<Instant> = timestamp(name = "start_time")
    val endTime: Column<Instant> = timestamp(name = "end_time")
    val sourceCreatedTime: Column<Instant?> = timestamp("source_created_time").nullable()
    val sourceUpdatedTime: Column<Instant?> = timestamp("source_updated_time").nullable()
    val notionId: Column<String?> = varchar(name = "notion_id", length = 50).nullable()

    override val primaryKey = PrimaryKey(id, name = "pk_reservation_id")
}

class ReservationEntity(id: EntityID<String>) : BaseEntity<String>(id, ReservationTable) {
    companion object : EntityClass<String, ReservationEntity>(ReservationTable)

    var startTime: Instant by ReservationTable.startTime
    var endTime: Instant by ReservationTable.endTime
    var summary: String? by ReservationTable.summary
    var description: String? by ReservationTable.description
    var guests by GuestEntity via ReservationGuestTable
    var sourceCreatedTime: Instant? by ReservationTable.sourceCreatedTime
    var sourceUpdatedTime: Instant? by ReservationTable.sourceUpdatedTime
    var notionId: String? by ReservationTable.notionId
}

fun daoToModel(dao: ReservationEntity) = Reservation(
    id = dao.id.value,
    summary = dao.summary,
    description = dao.description,
    startTime = dao.startTime,
    endTime = dao.endTime,
    guestIds = listOf(),
    sourceCreatedTime = dao.sourceCreatedTime,
    sourceUpdatedTime = dao.sourceUpdatedTime,
    notionId = dao.notionId,
)
