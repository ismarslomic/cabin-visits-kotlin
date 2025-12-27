package no.slomic.smarthytte.schema.guests

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("A person that has visited the cabin at least once.")
data class Guest(
    val id: String,
    val firstName: String,
    val lastName: String,
    val birthYear: Int,
    val gender: Gender,
    val email: String? = null,
)

@Suppress("unused")
enum class Gender {
    MALE,
    FEMALE,
}
