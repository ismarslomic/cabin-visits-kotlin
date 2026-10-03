package no.slomic.smarthytte.guests

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

/**
 * JPEG bytes, about 15-30 KB per guest. Kept in a separate table so the image is not loaded every time a guest is
 * read. A guest without avatar has no row.
 */
object GuestAvatarTable : Table(name = "guest_avatar") {
    val guest: Column<EntityID<String>> =
        reference("guest_id", GuestTable.id, onDelete = ReferenceOption.CASCADE)
    val image: Column<ExposedBlob> = blob("image")
    val updatedTime: Column<Instant> = timestamp("updated_time")

    override val primaryKey = PrimaryKey(guest, name = "pk_guest_avatar_guest_id")
}
