package no.slomic.smarthytte.sync.checkpoint

import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column

object SyncCheckpointTable : BaseIdTable<String>(name = "sync_checkpoint") {
    override val id: Column<EntityID<String>> = varchar(name = "id", length = 50).entityId()
    val checkpointValue: Column<String> = varchar(name = "checkpoint_value", length = 100)

    override val primaryKey = PrimaryKey(firstColumn = id, name = "pk_sync_checkpoint_id")
}

class SyncCheckpointEntity(id: EntityID<String>) : BaseEntity<String>(id, SyncCheckpointTable) {
    companion object : EntityClass<String, SyncCheckpointEntity>(SyncCheckpointTable)

    var checkpointValue: String by SyncCheckpointTable.checkpointValue
}
