package no.slomic.smarthytte.vehicletrips.jlr

import no.slomic.smarthytte.common.suspendReadTransaction
import no.slomic.smarthytte.common.suspendTransaction
import no.slomic.smarthytte.common.truncatedToMillis
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

class SqliteJlrTokenRepository(private val clock: Clock = Clock.System) : JlrTokenRepository {
    override suspend fun findRefreshToken(): String? = suspendReadTransaction {
        JlrTokenTable.selectAll().singleOrNull()?.get(JlrTokenTable.refreshToken)
    }

    override suspend fun saveRefreshToken(refreshToken: String) {
        suspendTransaction {
            JlrTokenTable.upsert {
                it[id] = JlrTokenTable.SINGLE_ROW_ID
                it[this.refreshToken] = refreshToken
                it[updatedTime] = clock.now().truncatedToMillis()
            }
        }
    }

    override suspend fun deleteRefreshToken() {
        suspendTransaction { JlrTokenTable.deleteAll() }
    }
}
