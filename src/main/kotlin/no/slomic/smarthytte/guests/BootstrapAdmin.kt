package no.slomic.smarthytte.guests

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import no.slomic.smarthytte.common.SYSTEM_ACTOR
import no.slomic.smarthytte.properties.BootstrapAdminProperties

sealed interface BootstrapResult {
    /** No `BOOTSTRAP_ADMIN_*` variable is set. */
    data object NotConfigured : BootstrapResult

    /** A guest already has the admin role, nothing was done. */
    data object AdminExists : BootstrapResult

    data class Created(val guestId: String) : BootstrapResult

    /** An existing guest with the email got the admin role. */
    data class Promoted(val guestId: String) : BootstrapResult
}

/**
 * Gives an empty database its first admin from the `BOOTSTRAP_ADMIN_*` variables, so development, CI and disaster
 * recovery need no SQL. It only acts when **no** guest has the admin role, so in production it does nothing once an
 * admin exists.
 *
 * With all five variables set it creates the guest as an admin (same validation and id rule as `POST /api/guests`,
 * actor `system`), or, if a guest with that email exists, gives that guest the admin role and logs a warning with the
 * guest id. No variables do nothing. Partly set or invalid variables fail startup with a message that names them, also
 * when an admin already exists, so a typo is found at once.
 *
 * The admin also needs an entry in the Cloudflare Access policy to log in.
 */
class BootstrapAdmin(
    private val guestRepository: GuestRepository,
    private val guestAdminService: GuestAdminService = GuestAdminService(guestRepository),
) {
    private val logger: Logger = KtorSimpleLogger(BootstrapAdmin::class.java.name)

    suspend fun run(properties: BootstrapAdminProperties): BootstrapResult {
        val values = properties.asMap()
        if (values.values.all { it.isBlank() }) return BootstrapResult.NotConfigured

        val request = properties.toCreateRequest()

        return if (guestRepository.hasAdmin()) {
            BootstrapResult.AdminExists
        } else {
            val existing = guestRepository.guestsByEmail(properties.email).firstOrNull()
            if (existing == null) create(request) else promote(existing)
        }
    }

    private suspend fun create(request: GuestCreate): BootstrapResult =
        when (val result = guestAdminService.create(request, SYSTEM_ACTOR)) {
            is GuestCreateResult.Created -> {
                logger.info("Created the first admin ${result.guest.id} from BOOTSTRAP_ADMIN_*")
                BootstrapResult.Created(result.guest.id)
            }

            is GuestCreateResult.Invalid -> error("BOOTSTRAP_ADMIN_* is invalid: ${result.message}")

            GuestCreateResult.EmailInUse -> error("BOOTSTRAP_ADMIN_EMAIL is used by another guest")
        }

    private suspend fun promote(guest: Guest): BootstrapResult {
        val outcome = guestRepository.update(
            guestId = guest.id,
            expectedVersion = guest.version,
            update = guest.toProperties().copy(role = AppRole.ADMIN),
            actor = SYSTEM_ACTOR,
        )
        check(outcome == GuestUpdateOutcome.Updated || outcome == GuestUpdateOutcome.Unchanged) {
            "Could not give the admin role to guest ${guest.id} from BOOTSTRAP_ADMIN_EMAIL: $outcome"
        }

        logger.warn("Gave the admin role to the existing guest ${guest.id}, since BOOTSTRAP_ADMIN_EMAIL matched it")
        return BootstrapResult.Promoted(guest.id)
    }
}

private fun BootstrapAdminProperties.asMap() = linkedMapOf(
    "BOOTSTRAP_ADMIN_EMAIL" to email,
    "BOOTSTRAP_ADMIN_FIRST_NAME" to firstName,
    "BOOTSTRAP_ADMIN_LAST_NAME" to lastName,
    "BOOTSTRAP_ADMIN_BIRTH_YEAR" to birthYear,
    "BOOTSTRAP_ADMIN_GENDER" to gender,
)

/** The guest to create, or a startup failure that names the variables that are missing or cannot be read. */
private fun BootstrapAdminProperties.toCreateRequest(): GuestCreate {
    val missing = asMap().filterValues { it.isBlank() }.keys
    check(missing.isEmpty()) { "BOOTSTRAP_ADMIN_* is partly set, these are missing: ${missing.joinToString()}" }

    val year = checkNotNull(birthYear.trim().toShortOrNull()) { "BOOTSTRAP_ADMIN_BIRTH_YEAR must be a year" }
    val parsedGender = checkNotNull(Gender.entries.firstOrNull { it.name.equals(gender.trim(), ignoreCase = true) }) {
        "BOOTSTRAP_ADMIN_GENDER must be one of ${Gender.entries.joinToString()}"
    }

    return GuestCreate(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        birthYear = year,
        gender = parsedGender,
        email = normalizedEmail(email),
        role = AppRole.ADMIN,
    ).also { request ->
        invalidGuestReason(request.firstName, request.lastName, request.birthYear, request.email)?.let {
            error("BOOTSTRAP_ADMIN_* is invalid: $it")
        }
    }
}
