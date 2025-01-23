package no.slomic.smarthytte.guest

import kotlinx.serialization.Serializable

@Serializable
data class Guest(
    val id: String,
    val firstName: String,
    val lastName: String,
    val birthYear: Short,
    val email: String? = null,
    val gender: Gender,
    val notionId: String? = null,
)

@Suppress("unused")
enum class Gender {
    MALE,
    FEMALE,
}
