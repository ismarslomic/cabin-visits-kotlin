package no.slomic.smarthytte.guests

/**
 * Reads of guests for other packages and for routes (the avatar reads are in [GuestAvatarService]). Other packages
 * call this instead of the [GuestRepository]. The reads apply no access control: the route that serves them decides
 * who may call it.
 */
class GuestQueryService(private val guestRepository: GuestRepository) {
    suspend fun allGuests(): List<Guest> = guestRepository.allGuests()

    /** The guests with the [email], ignoring case and surrounding whitespace. */
    suspend fun guestsByEmail(email: String): List<Guest> = guestRepository.guestsByEmail(email)
}
