package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable

/** Application roles, stored in `guest.role`. A guest without a role has no access to protected endpoints. */
@Serializable
enum class AppRole {
    /** Manages users, guests and all reservations. */
    ADMIN,

    /** Manages all reservations and edits own profile. */
    FAMILY_USER,

    /** Limited read-only access, edits own profile. */
    GUEST_USER,
}
