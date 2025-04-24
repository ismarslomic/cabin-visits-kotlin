package no.slomic.smarthytte.reservations

import kotlinx.datetime.Instant
import no.slomic.smarthytte.checkinouts.CheckIn
import no.slomic.smarthytte.checkinouts.CheckInOutSource
import no.slomic.smarthytte.checkinouts.CheckOut
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
    val checkInTime: Column<Instant?> = timestamp("check_in_time").nullable()
    val checkInSourceName: Column<CheckInOutSource?> =
        enumerationByName("check_in_source_name", length = 20, CheckInOutSource::class).nullable()
    val checkInSourceId: Column<String?> = varchar(name = "check_in_source_id", length = 1024).nullable()
    val checkOutTime: Column<Instant?> = timestamp("check_out_time").nullable()
    val checkOutSourceName: Column<CheckInOutSource?> =
        enumerationByName("check_out_source_name", length = 20, CheckInOutSource::class).nullable()
    val checkOutSourceId: Column<String?> = varchar(name = "check_out_source_id", length = 1024).nullable()
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
    var checkInTime: Instant? by ReservationTable.checkInTime
    var checkInSourceName: CheckInOutSource? by ReservationTable.checkInSourceName
    var checkInSourceId: String? by ReservationTable.checkInSourceId
    var checkOutTime: Instant? by ReservationTable.checkOutTime
    var checkOutSourceName: CheckInOutSource? by ReservationTable.checkOutSourceName
    var checkOutSourceId: String? by ReservationTable.checkOutSourceId
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
    checkIn = dao.checkInTime?.let {
        CheckIn(
            time = it,
            sourceName = dao.checkInSourceName!!,
            sourceId = dao.checkInSourceId!!,
        )
    },
    checkOut = dao.checkOutTime?.let {
        CheckOut(
            time = it,
            sourceName = dao.checkOutSourceName!!,
            sourceId = dao.checkOutSourceId!!,
        )
    },
)
