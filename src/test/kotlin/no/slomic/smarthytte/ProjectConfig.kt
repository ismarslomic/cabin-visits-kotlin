package no.slomic.smarthytte

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.AfterProjectListener
import no.slomic.smarthytte.utils.ResponseCodeAudit

private const val AUDIT_PROPERTY = "auditResponseCodes"

/**
 * Fails the run when a response code of the OpenAPI spec was never answered by a test ([ResponseCodeAudit]). Only when
 * the whole suite ran: Gradle sets [AUDIT_PROPERTY] to true unless the run was limited with `--tests`.
 */
object ResponseCodeAuditListener : AfterProjectListener {
    override suspend fun afterProject() {
        if (System.getProperty(AUDIT_PROPERTY) != "true") return

        val missing = ResponseCodeAudit.missing()
        check(missing.isEmpty()) {
            "Response codes in openapi/cabin-visits.json that no test receives. Add a test, or an entry with a " +
                "reason to ResponseCodeAudit.untestable:\n" + missing.joinToString("\n")
        }
    }
}

class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(ResponseCodeAuditListener)
}
