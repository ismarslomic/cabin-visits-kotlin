package no.slomic.smarthytte.auth

import kotlinx.serialization.Serializable

/** Application roles, stored in `guest.app_role`. A guest without a role has no access to protected endpoints. */
@Serializable
enum class AppRole {
    /** Manages users and all reservations. */
    ADMIN,

    /** Views relevant information and manages own reservations. */
    FAMILY_USER,

    /** Limited read-only access. */
    GUEST_USER,
}
