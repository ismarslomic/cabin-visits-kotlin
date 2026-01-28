package no.slomic.smarthytte.skistats

import no.slomic.smarthytte.common.BaseEntity
import no.slomic.smarthytte.common.BaseIdTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.EntityClass

object SkiStatsTokenTable : BaseIdTable<String>(name = "ski_stats_token") {
    override val id: Column<EntityID<String>> = varchar("id", length = 50).entityId()
    val accessToken: Column<String> = text("access_token")
    val refreshToken: Column<String> = text("refresh_token")
    val expiresAtEpochSeconds: Column<Long?> = long("expires_at_epoch_seconds").nullable()

    override val primaryKey = PrimaryKey(id, name = "pk_ski_stats_token_id")
}

class SkiStatsTokenEntity(id: EntityID<String>) : BaseEntity<String>(id, SkiStatsTokenTable) {
    companion object : EntityClass<String, SkiStatsTokenEntity>(SkiStatsTokenTable)

    var accessToken: String by SkiStatsTokenTable.accessToken
    var refreshToken: String by SkiStatsTokenTable.refreshToken
    var expiresAtEpochSeconds: Long? by SkiStatsTokenTable.expiresAtEpochSeconds
}

fun daoToModel(dao: SkiStatsTokenEntity) = SkiStatsTokens(
    accessToken = dao.accessToken,
    refreshToken = dao.refreshToken,
    expiresAtEpochSeconds = dao.expiresAtEpochSeconds,
)
