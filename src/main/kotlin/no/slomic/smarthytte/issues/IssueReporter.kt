package no.slomic.smarthytte.issues

/**
 * Reports operational problems that need attention, at the boundary that decides what happens (code style 5).
 *
 * The message is stored in the database and returned by the API, so write it to be safe to store: status codes,
 * error codes and class names, never a response body, URL, token or key. Only the class name of the cause is stored,
 * never its message.
 */
interface IssueReporter {
    /**
     * @param key dedupe key, e.g. `PLACE_MISSING:<tripId>` or `JLR_APPROVAL_REQUIRED`
     */
    suspend fun report(code: IssueCode, key: String, message: String, cause: Throwable? = null)

    /** Tells that the problem of [code] and [key] is gone. Nothing happens when no such issue is open. */
    suspend fun resolve(code: IssueCode, key: String)
}
