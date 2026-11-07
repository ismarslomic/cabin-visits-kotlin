package no.slomic.smarthytte.guests

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import kotlin.time.Instant

@Serializable
data class Guest(
    val id: String,
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val email: String? = null,
    val gender: Gender,
    val notionId: String? = null,
    val isFamily: Boolean = false,
    val role: AppRole? = null,
) {
    // Set by the repository when a stored guest is read. Not part of equals/hashCode/copy (decision 34): never copy a
    // loaded guest and write it back. Not serialized, the API responses carry them in their own classes.
    @Transient
    var version: Short = 1
        internal set

    @Transient
    var createdTime: Instant = Instant.DISTANT_PAST
        internal set

    @Transient
    var createdBy: String = SYSTEM_ACTOR
        internal set

    @Transient
    var updatedTime: Instant? = null
        internal set

    @Transient
    var updatedBy: String? = null
        internal set
}

@Suppress("unused")
enum class Gender {
    MALE,
    FEMALE,
}
