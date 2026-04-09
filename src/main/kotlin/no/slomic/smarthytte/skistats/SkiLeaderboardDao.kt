package no.slomic.smarthytte.skistats

import kotlinx.datetime.LocalDate
import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp

object SkiProfileTable : BaseIdTable<String>(name = "ski_stats_profile") {
    override val id: Column<EntityID<String>> = varchar("id", length = 50).entityId()
    val name: Column<String> = varchar("name", length = 100)
    val profileImageUrl: Column<String?> = varchar("profile_image_url", length = 500).nullable()
    val isPrivate: Column<Boolean> = bool("is_private")

    override val primaryKey = PrimaryKey(id, name = "pk_ski_stats_profile_id")
}

class SkiProfileEntity(id: EntityID<String>) : BaseEntity<String>(id, SkiProfileTable) {
    companion object : EntityClass<String, SkiProfileEntity>(SkiProfileTable)

    var name: String by SkiProfileTable.name
    var profileImageUrl: String? by SkiProfileTable.profileImageUrl
    var isPrivate: Boolean by SkiProfileTable.isPrivate
}

fun daoToModel(dao: SkiProfileEntity) = SkiProfile(
    id = dao.id.value,
    name = dao.name,
    profileImageUrl = dao.profileImageUrl,
    isPrivate = dao.isPrivate,
)

object SkiLeaderboardEntryTable : BaseIdTable<String>(name = "ski_stats_leaderboard_entry") {
    override val id: Column<EntityID<String>> = varchar("id", length = 100).entityId()
    val profileId: Column<String> = varchar("profile_id", length = 50)
    val periodType: Column<String> = varchar("period_type", length = 10)
    val periodValue: Column<String> = varchar("period_value", length = 20)
    val startDate: Column<String> = varchar("start_date", length = 10)
    val weekId: Column<String?> = varchar("week_id", length = 10).nullable()
    val seasonId: Column<String> = varchar("season_id", length = 10)
    val seasonName: Column<String> = varchar("season_name", length = 20)
    val year: Column<Int?> = integer("year").nullable()
    val weekNumber: Column<Int?> = integer("week_number").nullable()
    val leaderboardUpdatedAtUtc = timestamp("leaderboard_updated_at_utc")
    val entryUserId: Column<String> = varchar("entry_user_id", length = 50)
        .references(SkiProfileTable.id, onDelete = ReferenceOption.CASCADE)
    val position: Column<Int> = integer("position")
    val dropHeightInMeter: Column<Int> = integer("drop_height_in_meter")

    override val primaryKey = PrimaryKey(id, name = "pk_ski_stats_leaderboard_entry_id")
}

class SkiLeaderboardEntryEntity(id: EntityID<String>) : BaseEntity<String>(id, SkiLeaderboardEntryTable) {
    companion object : EntityClass<String, SkiLeaderboardEntryEntity>(SkiLeaderboardEntryTable)

    var profileId: String by SkiLeaderboardEntryTable.profileId
    var periodType: String by SkiLeaderboardEntryTable.periodType
    var periodValue: String by SkiLeaderboardEntryTable.periodValue
    var startDate: String by SkiLeaderboardEntryTable.startDate
    var weekId: String? by SkiLeaderboardEntryTable.weekId
    var seasonId: String by SkiLeaderboardEntryTable.seasonId
    var seasonName: String by SkiLeaderboardEntryTable.seasonName
    var year: Int? by SkiLeaderboardEntryTable.year
    var weekNumber: Int? by SkiLeaderboardEntryTable.weekNumber
    var leaderboardUpdatedAtUtc by SkiLeaderboardEntryTable.leaderboardUpdatedAtUtc
    var entryUserId: String by SkiLeaderboardEntryTable.entryUserId
    var position: Int by SkiLeaderboardEntryTable.position
    var dropHeightInMeter: Int by SkiLeaderboardEntryTable.dropHeightInMeter
}

fun daoToModel(dao: SkiLeaderboardEntryEntity) = SkiLeaderboardEntry(
    id = dao.id.value,
    profileId = dao.profileId,
    period = SkiLeaderboardPeriod(
        type = PeriodType.valueOf(dao.periodType),
        value = dao.periodValue,
        startDate = LocalDate.parse(dao.startDate),
        weekId = dao.weekId,
        seasonId = dao.seasonId,
        seasonName = dao.seasonName,
        year = dao.year,
        weekNumber = dao.weekNumber,
    ),
    leaderboardUpdatedAtUtc = dao.leaderboardUpdatedAtUtc,
    entryUserId = dao.entryUserId,
    position = dao.position,
    dropHeightInMeter = dao.dropHeightInMeter,
)
