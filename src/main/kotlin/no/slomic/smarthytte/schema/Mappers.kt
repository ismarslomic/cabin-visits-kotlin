package no.slomic.smarthytte.schema

import no.slomic.smarthytte.checkinouts.CheckIn as CheckInDomain
import no.slomic.smarthytte.checkinouts.CheckInOutSource as CheckInOutSourceDomain
import no.slomic.smarthytte.checkinouts.CheckOut as CheckOutDomain
import no.slomic.smarthytte.guests.Gender as GenderDomain
import no.slomic.smarthytte.guests.Guest as GuestDomain
import no.slomic.smarthytte.reservations.Reservation as ReservationDomain
import no.slomic.smarthytte.schema.models.CheckIn as CheckInGql
import no.slomic.smarthytte.schema.models.CheckInOutSource as CheckInOutSourceGql
import no.slomic.smarthytte.schema.models.CheckOut as CheckOutGql
import no.slomic.smarthytte.schema.models.Gender as GenderGql
import no.slomic.smarthytte.schema.models.Guest as GuestGql
import no.slomic.smarthytte.schema.models.Reservation as ReservationGql

fun GuestDomain.toGql(): GuestGql = GuestGql(
    id = this.id,
    firstName = this.firstName,
    lastName = this.lastName,
    birthYear = this.birthYear.toInt(),
    gender = this.gender.toGql(),
    email = this.email,
)

fun GenderDomain.toGql(): GenderGql = when (this) {
    GenderDomain.MALE -> GenderGql.MALE
    GenderDomain.FEMALE -> GenderGql.FEMALE
}

fun ReservationDomain.toGql(): ReservationGql = ReservationGql(
    id = id,
    startTime = startTime,
    endTime = endTime,
    guestIds = guestIds,
    summary = summary,
    description = description,
    sourceCreatedTime = sourceCreatedTime,
    sourceUpdatedTime = sourceUpdatedTime,
    checkIn = checkIn?.toGql(),
    checkOut = checkOut?.toGql(),
)

fun CheckInDomain.toGql(): CheckInGql = CheckInGql(
    time = time,
    sourceName = sourceName.toGql(),
    sourceId = sourceId,
)

fun CheckOutDomain.toGql(): CheckOutGql = CheckOutGql(
    time = time,
    sourceName = sourceName.toGql(),
    sourceId = sourceId,
)

fun CheckInOutSourceDomain.toGql(): CheckInOutSourceGql = when (this) {
    CheckInOutSourceDomain.CHECK_IN_SENSOR -> CheckInOutSourceGql.CHECK_IN_SENSOR
    CheckInOutSourceDomain.VEHICLE_TRIP -> CheckInOutSourceGql.VEHICLE_TRIP
    CheckInOutSourceDomain.CALENDAR_EVENT -> CheckInOutSourceGql.CALENDAR_EVENT
}
