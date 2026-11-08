package no.slomic.smarthytte.guests

/**
 * The writable properties of a guest, all of them: what the repository stores when a guest is created
 * ([GuestRepository.create]) and what it replaces in a full update ([GuestRepository.update]), where `null` for [email]
 * or [role] clears it. Not serialized: the HTTP requests are [GuestCreate] (some properties optional), [GuestUpdate]
 * and [MeUpdate] (all required), and each converts to this class.
 */
data class GuestProperties(
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val gender: Gender,
    val email: String?,
    val isFamily: Boolean,
    val role: AppRole?,
)
