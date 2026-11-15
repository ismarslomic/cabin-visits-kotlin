package no.slomic.smarthytte.vehicletrips.jlr

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

/** Holds at most one row, with [id] always [SINGLE_ROW_ID]. Internal, so no audit columns. */
object JlrTokenTable : Table(name = "jlr_token") {
    const val SINGLE_ROW_ID = 1

    val id: Column<Int> = integer("id")
    val refreshToken: Column<String> = text("refresh_token")
    val updatedTime: Column<Instant> = timestamp("updated_time")

    override val primaryKey = PrimaryKey(id, name = "pk_jlr_token_id")
}
