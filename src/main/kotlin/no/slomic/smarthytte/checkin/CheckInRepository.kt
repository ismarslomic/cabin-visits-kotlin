package no.slomic.smarthytte.checkin

import kotlinx.datetime.Instant

interface CheckInRepository {
    suspend fun allCheckIns(): List<CheckIn>
    suspend fun addOrUpdate(checkIn: CheckIn): CheckIn
    suspend fun lastCheckInTimestamp(): Instant?
    suspend fun addOrUpdate(latestCheckInTimestamp: Instant)
}
