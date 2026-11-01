package no.slomic.smarthytte.auth

import kotlinx.serialization.Serializable
import no.slomic.smarthytte.guests.AppRole
import no.slomic.smarthytte.guests.Gender

/** Who made an authenticated request: a person ([UserPrincipal]) or a machine ([ServicePrincipal]). */
sealed interface AppPrincipal

/**
 * A person authenticated through Cloudflare Access: an existing guest record that has been granted an [AppRole].
 * What the user may do is decided by [role]. [name] is the [firstName] and [lastName] together.
 */
@Serializable
data class UserPrincipal(
    val id: String,
    val name: String,
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val gender: Gender,
    val email: String,
    val isFamily: Boolean,
    val role: AppRole,
) : AppPrincipal

/**
 * A machine, such as MagicMirror, authenticated with an API key.
 *
 * A service has no role: it may only call the routes that explicitly allow services (`allowService = true` in
 * [authorized]), and nothing else.
 */
data class ServicePrincipal(val name: String) : AppPrincipal
