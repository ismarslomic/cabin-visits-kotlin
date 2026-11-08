package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.toUtcDate
import kotlin.time.Clock

internal const val MAX_NAME_LENGTH = 20
internal const val MAX_EMAIL_LENGTH = 254
internal const val MIN_BIRTH_YEAR = 1900
private val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s]+$")

/**
 * The first rule that the given properties break, or null. Properties that are null are not checked: they are
 * unchanged in an update. The email is expected to be normalised already ([normalizedEmail]).
 */
internal fun invalidGuestReason(firstName: String?, lastName: String?, birthYear: Short?, email: String?): String? =
    listOfNotNull(
        "firstName must be 1-$MAX_NAME_LENGTH characters".takeIf { firstName.isInvalidName() },
        "lastName must be 1-$MAX_NAME_LENGTH characters".takeIf { lastName.isInvalidName() },
        "birthYear must be between $MIN_BIRTH_YEAR and the current year".takeIf { birthYear.isInvalidYear() },
        "email must be a valid address of at most $MAX_EMAIL_LENGTH characters".takeIf { email.isInvalidEmail() },
    ).firstOrNull()

/**
 * The one email normalisation of the application: trimmed and lowercase, so the same address is always stored the
 * same way. A blank email becomes null (no email), since an empty string stored twice would break the unique index.
 * Plus-addresses (`ola+x@example.com`) are different identities.
 */
internal fun normalizedEmail(email: String?): String? = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

private fun String?.isInvalidName() = this != null && trim().length !in 1..MAX_NAME_LENGTH

private fun Short?.isInvalidYear() = this != null && toInt() !in MIN_BIRTH_YEAR..Clock.System.now().toUtcDate().year

private fun String?.isInvalidEmail() = this != null && (length > MAX_EMAIL_LENGTH || !EMAIL_FORMAT.matches(this))
