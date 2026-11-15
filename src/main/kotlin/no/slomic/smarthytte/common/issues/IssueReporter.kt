package no.slomic.smarthytte.common.issues

/**
 * Reports operational problems that need attention, at the boundary that decides what happens (code style 5).
 *
 * Messages must never contain tokens or keys.
 */
interface IssueReporter {
    /**
     * @param key dedupe key, e.g. `PLACE_MISSING:<tripId>` or `JLR_APPROVAL_REQUIRED`
     */
    fun report(code: IssueCode, key: String, message: String, cause: Throwable? = null)
}
