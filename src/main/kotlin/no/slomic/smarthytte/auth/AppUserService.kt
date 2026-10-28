package no.slomic.smarthytte.auth

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Guest
import no.slomic.smarthytte.guests.GuestRepository

/** Maps the identity of Cloudflare Access to a guest, so persons are never duplicated. */
class AppUserService(private val guestRepository: GuestRepository) {
    private val logger: Logger = KtorSimpleLogger(AppUserService::class.java.name)

    /**
     * The guest with an [AppRole] and the given [email] (case-insensitive), or null if there is none.
     *
     * Also null when several guests with a role share the email: the identity is ambiguous, so access is denied.
     */
    suspend fun userByEmail(email: String?): UserPrincipal? {
        val guests: List<Guest> = email?.takeIf { it.isNotBlank() }
            ?.let { guestRepository.guestsByEmail(it) }
            .orEmpty()
            .filter { it.appRole != null }

        if (guests.size > 1) {
            logger.warn("Ambiguous identity: ${guests.size} guests with a role share the email $email")
        }

        return guests.singleOrNull()?.toUserPrincipal()
    }
}

/** Null if the guest has no email or no [AppRole], since it is then not a user of the application. */
fun Guest.toUserPrincipal(): UserPrincipal? = if (email != null && appRole != null) {
    UserPrincipal(id = id, name = "$firstName $lastName", email = email, role = appRole)
} else {
    null
}
