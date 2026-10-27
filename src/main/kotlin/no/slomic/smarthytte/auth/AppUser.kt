package no.slomic.smarthytte.auth

import kotlinx.serialization.Serializable
import no.slomic.smarthytte.guests.AppRole

/** The authenticated user: an existing guest record that has been granted an [AppRole]. */
@Serializable
data class AppUser(val id: String, val name: String, val email: String, val role: AppRole)
