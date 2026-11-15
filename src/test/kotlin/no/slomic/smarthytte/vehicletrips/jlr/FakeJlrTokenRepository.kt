package no.slomic.smarthytte.vehicletrips.jlr

/** Hand-written fake of the token store. [failNextSave] makes the next save throw, to test a lost write. */
class FakeJlrTokenRepository(var stored: String? = null) : JlrTokenRepository {
    val saved = mutableListOf<String>()
    var deleteCount = 0
    var failNextSave = false

    override suspend fun findRefreshToken(): String? = stored

    override suspend fun saveRefreshToken(refreshToken: String) {
        if (failNextSave) {
            failNextSave = false
            error("disk full")
        }
        stored = refreshToken
        saved += refreshToken
    }

    override suspend fun deleteRefreshToken() {
        stored = null
        deleteCount++
    }
}
