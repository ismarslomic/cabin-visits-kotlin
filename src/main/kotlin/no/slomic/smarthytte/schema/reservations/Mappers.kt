package no.slomic.smarthytte.schema.reservations

import no.slomic.smarthytte.common.toIsoUtcString
import no.slomic.smarthytte.checkinouts.CheckIn as CheckInDomain
import no.slomic.smarthytte.checkinouts.CheckInOutSource as CheckInOutSourceDomain
import no.slomic.smarthytte.checkinouts.CheckOut as CheckOutDomain
import no.slomic.smarthytte.reservations.Reservation as ReservationDomain
import no.slomic.smarthytte.schema.reservations.CheckIn as CheckInGql
import no.slomic.smarthytte.schema.reservations.CheckInOutSource as CheckInOutSourceGql
import no.slomic.smarthytte.schema.reservations.CheckOut as CheckOutGql
import no.slomic.smarthytte.schema.reservations.Reservation as ReservationGql

fun ReservationDomain.toGql(): ReservationGql = ReservationGql(
    id = id,
    startTime = startTime.toIsoUtcString(),
    endTime = endTime.toIsoUtcString(),
    guestIds = guestIds,
    summary = summary,
    description = description,
    sourceCreatedTime = sourceCreatedTime?.toIsoUtcString(),
    sourceUpdatedTime = sourceUpdatedTime?.toIsoUtcString(),
    checkIn = checkIn?.toGql(),
    checkOut = checkOut?.toGql(),
)

fun CheckInDomain.toGql(): CheckInGql = CheckInGql(
    time = time.toIsoUtcString(),
    sourceName = sourceName.toGql(),
    sourceId = sourceId,
)

fun CheckOutDomain.toGql(): CheckOutGql = CheckOutGql(
    time = time.toIsoUtcString(),
    sourceName = sourceName.toGql(),
    sourceId = sourceId,
)

fun CheckInOutSourceDomain.toGql(): CheckInOutSourceGql = when (this) {
    CheckInOutSourceDomain.CHECK_IN_SENSOR -> CheckInOutSourceGql.CHECK_IN_SENSOR
    CheckInOutSourceDomain.VEHICLE_TRIP -> CheckInOutSourceGql.VEHICLE_TRIP
    CheckInOutSourceDomain.CALENDAR_EVENT -> CheckInOutSourceGql.CALENDAR_EVENT
}
