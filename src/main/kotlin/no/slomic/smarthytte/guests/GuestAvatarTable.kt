package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.AuditedEntity
import no.slomic.smarthytte.common.AuditedIdTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.dao.EntityClass
import kotlin.time.Instant

/**
 * JPEG bytes, about 15-30 KB per guest. Kept in a separate table so the image is not loaded every time a guest is
 * read. A guest without avatar has no row. The id is the guest's id (primary key and foreign key in one).
 */
object GuestAvatarTable : AuditedIdTable<String>(name = "guest_avatar") {
    override val id: Column<EntityID<String>> =
        reference("guest_id", GuestTable, onDelete = ReferenceOption.CASCADE)
    val image: Column<ExposedBlob> = blob("image")

    override val primaryKey = PrimaryKey(id, name = "pk_guest_avatar_guest_id")
}

class GuestAvatarEntity(id: EntityID<String>) : AuditedEntity<String>(id, GuestAvatarTable) {
    companion object : EntityClass<String, GuestAvatarEntity>(GuestAvatarTable)

    var image: ExposedBlob by GuestAvatarTable.image

    /** When the image was last changed: `updated_time`, or `created_time` for an avatar never replaced. */
    fun versionTime(): Instant = updatedTime ?: createdTime
}
