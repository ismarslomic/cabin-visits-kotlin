package no.slomic.smarthytte.sensors.checkinouts

import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

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
