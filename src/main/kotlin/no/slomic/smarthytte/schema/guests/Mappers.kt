package no.slomic.smarthytte.schema.guests

import no.slomic.smarthytte.guests.Gender as DomainGender
import no.slomic.smarthytte.guests.Guest as DomainGuest

fun DomainGuest.toGql(): Guest = Guest(
    id = this.id,
    firstName = this.firstName,
    lastName = this.lastName,
    birthYear = this.birthYear.toInt(),
    gender = this.gender.toGql(),
    email = this.email,
)

fun DomainGender.toGql(): Gender = when (this) {
    DomainGender.MALE -> Gender.MALE
    DomainGender.FEMALE -> Gender.FEMALE
}
