package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.dao.EntityClass

object GuestTable : BaseIdTable<String>(name = "guest") {
    override val id: Column<EntityID<String>> = varchar("id", length = 50).entityId()
    val firstName: Column<String> = varchar(name = "first_name", length = 20)
    val lastName: Column<String> = varchar(name = "last_name", length = 20)
    val birthYear: Column<Short> = short("birth_year")
    val email: Column<String?> = varchar(name = "email", length = 255).nullable()
    val gender: Column<Gender> = enumerationByName("gender", length = 10, Gender::class)
    val notionId: Column<String?> = varchar(name = "notion_id", length = 50).nullable()
    val isFamily: Column<Boolean> = bool("is_family").default(false)

    // JPEG bytes, about 15-30 KB. ExposedBlob (not binary/ByteArray) compares by content, so the entity's
    // writeValues dirty check skips unchanged images. Note that GuestEntity.all() loads this column for every guest;
    // if that becomes a problem, move the image to a separate guest_avatar table.
    val avatarImage: Column<ExposedBlob?> = blob("avatar_image").nullable()

    override val primaryKey = PrimaryKey(id, name = "pk_guest_id")
}

class GuestEntity(id: EntityID<String>) : BaseEntity<String>(id, GuestTable) {
    companion object : EntityClass<String, GuestEntity>(GuestTable)

    var firstName: String by GuestTable.firstName
    var lastName: String by GuestTable.lastName
    var birthYear: Short by GuestTable.birthYear
    var email: String? by GuestTable.email
    var gender: Gender by GuestTable.gender
    var notionId: String? by GuestTable.notionId
    var isFamily: Boolean by GuestTable.isFamily
    var avatarImage: ExposedBlob? by GuestTable.avatarImage
}

fun daoToModel(dao: GuestEntity) = Guest(
    id = dao.id.value,
    firstName = dao.firstName,
    lastName = dao.lastName,
    birthYear = dao.birthYear,
    email = dao.email,
    gender = dao.gender,
    notionId = dao.notionId,
    isFamily = dao.isFamily,
)
