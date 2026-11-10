package no.slomic.smarthytte

import no.slomic.smarthytte.auth.ApiKeyConfig
import no.slomic.smarthytte.auth.AppUserService
import no.slomic.smarthytte.auth.CloudflareAccessConfig
import no.slomic.smarthytte.guests.GuestAdminService
import no.slomic.smarthytte.guests.GuestProfileService
import no.slomic.smarthytte.guests.GuestRepository
import no.slomic.smarthytte.reservations.ReservationService
import no.slomic.smarthytte.statistics.StatsService

/**
 * Everything [configureApiRoutes] needs, used by production code and tests. A route group that needs a new service
 * adds a property here, so the call sites do not change.
 */
class ApiDependencies(
    val guestRepository: GuestRepository,
    val guestProfileService: GuestProfileService,
    val guestAdminService: GuestAdminService,
    val statsService: StatsService,
    val reservationService: ReservationService,
    val appUserService: AppUserService,
    val cloudflareAccess: CloudflareAccessConfig?,
    val apiKeys: ApiKeyConfig = ApiKeyConfig(emptyMap()),
)
