package no.slomic.smarthytte.guests

import no.slomic.smarthytte.common.SYSTEM_ACTOR
import java.text.Normalizer

/** The guest id column is `varchar(50)`. */
internal const val MAX_GUEST_ID_LENGTH = 50
private const val FALLBACK_SLUG = "guest"
private val NORWEGIAN_LETTERS = mapOf(
    'æ' to "ae",
    'ø' to "o",
    'å' to "a",
    'ö' to "o",
    'ä' to "a",
    'ü' to "u",
    'é' to "e",
)
private val DIACRITICS = Regex("\\p{M}+")
private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

/**
 * The slug of a guest's name, such as `ola.nordmann`: lowercase, Norwegian letters transliterated, every run of other
 * characters (after removing diacritics) replaced by `.`, at most [MAX_GUEST_ID_LENGTH] characters. `guest` if nothing
 * is left. Migration `V1.8` gives the existing guests the same ids, keep the two in step.
 */
internal fun guestSlug(firstName: String, lastName: String): String {
    val lowercase = "$firstName $lastName".lowercase()
    val transliterated = buildString { lowercase.forEach { append(NORWEGIAN_LETTERS[it] ?: it) } }
    val withoutDiacritics = Normalizer.normalize(transliterated, Normalizer.Form.NFKD).replace(DIACRITICS, "")

    return withoutDiacritics
        .replace(NOT_ALPHANUMERIC, ".")
        .trim('.')
        .take(MAX_GUEST_ID_LENGTH)
        .trim('.')
        .ifEmpty { FALLBACK_SLUG }
}

/**
 * The first free id for the [slug]: the slug itself, or with a numeric suffix (`ola.nordmann-2`). Ids are compared
 * ignoring case, and [SYSTEM_ACTOR] is reserved because the audit columns use it for changes that no guest made. The
 * slug is shortened before the suffix is added, so the id stays within [MAX_GUEST_ID_LENGTH].
 */
internal fun uniqueGuestId(slug: String, takenIds: Collection<String>): String {
    val taken = takenIds.mapTo(mutableSetOf()) { it.lowercase() } + SYSTEM_ACTOR

    return generateSequence(1) { it + 1 }
        .map { number -> if (number == 1) slug else suffixed(slug, number) }
        .first { it.lowercase() !in taken }
}

private fun suffixed(slug: String, number: Int): String {
    val suffix = "-$number"
    return slug.take(MAX_GUEST_ID_LENGTH - suffix.length).trimEnd('.') + suffix
}
