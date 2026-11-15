package no.slomic.smarthytte.properties

import com.sksamuel.hoplite.Masked

data class JlrPropertiesHolder(val jlr: JlrProperties)

/**
 * The Data Access Credential of the JLR Data Hub. [privateKey] is a secret: never log it.
 *
 * @param privateKey the PEM of the private key, a literal `\n` for line breaks is accepted
 * @param fetchIntervalHours outside the reservation window, trips are fetched when the last success is this old
 */
data class JlrProperties(val clientId: String, val vin: String, val privateKey: Masked, val fetchIntervalHours: Int) {
    override fun toString(): String = "JlrProperties(fetchIntervalHours=$fetchIntervalHours)"
}
