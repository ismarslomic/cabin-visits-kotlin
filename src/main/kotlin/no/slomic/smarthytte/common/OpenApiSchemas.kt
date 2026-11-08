package no.slomic.smarthytte.common

import io.ktor.openapi.JsonSchema

/**
 * The schema with every property required. The generator leaves nullable properties out of `required`, but in a full
 * update (`PUT`) a nullable property such as `email` must be sent too, `null` clears it. Only the `required` list
 * changes, so the schema is still the one shared component.
 */
fun JsonSchema.allPropertiesRequired(): JsonSchema = copy(required = properties?.keys?.toList())
