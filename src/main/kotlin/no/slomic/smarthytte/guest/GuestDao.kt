package no.slomic.smarthytte.guest

import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column

object GuestTable : BaseIdTable<String>(name = "guest") {
    override val id: Column<EntityID<String>> = varchar("id", length = 50).entityId()
    val firstName: Column<String> = varchar(name = "first_name", length = 20)
    val lastName: Column<String> = varchar(name = "last_name", length = 20)
    val birthYear: Column<Short> = short("birth_year")
    val email: Column<String?> = varchar(name = "email", length = 255).nullable()
    val gender: Column<Gender> = enumerationByName("gender", length = 10, Gender::class)
    val notionId: Column<String?> = varchar(name = "notion_id", length = 50).nullable()

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
}

fun daoToModel(dao: GuestEntity) = Guest(
    id = dao.id.value,
    firstName = dao.firstName,
    lastName = dao.lastName,
    birthYear = dao.birthYear,
    email = dao.email,
    gender = dao.gender,
    notionId = dao.notionId,
)
