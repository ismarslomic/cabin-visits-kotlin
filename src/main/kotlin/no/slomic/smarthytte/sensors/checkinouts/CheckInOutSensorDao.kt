package no.slomic.smarthytte.sensors.checkinouts

import kotlinx.datetime.Instant
import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object CheckInOutSensorTable : BaseIdTable<String>(name = "check_in_out_sensor") {
    override val id: Column<EntityID<String>> = varchar("id", length = 30).entityId()
    val time: Column<Instant> = timestamp(name = "time")
    val status: Column<CheckInStatus> = enumerationByName("status", length = 15, CheckInStatus::class)
    override val primaryKey = PrimaryKey(id, name = "pk_check_in_out_sensor_id")
}

class CheckInOutSensorEntity(id: EntityID<String>) : BaseEntity<String>(id, CheckInOutSensorTable) {
    companion object : EntityClass<String, CheckInOutSensorEntity>(CheckInOutSensorTable)

    var time: Instant by CheckInOutSensorTable.time
    var status: CheckInStatus by CheckInOutSensorTable.status
}

fun daoToModel(dao: CheckInOutSensorEntity) = CheckInOutSensor(
    id = dao.id.value,
    time = dao.time,
    status = dao.status,
)
