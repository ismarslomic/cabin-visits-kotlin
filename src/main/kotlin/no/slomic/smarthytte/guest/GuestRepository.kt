package no.slomic.smarthytte.guest

interface GuestRepository {
    suspend fun addOrUpdate(guest: Guest): Guest
}
