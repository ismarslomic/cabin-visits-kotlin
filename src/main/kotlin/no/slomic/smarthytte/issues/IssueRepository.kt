package no.slomic.smarthytte.issues

interface IssueRepository {
    /**
     * Stores an occurrence of the problem. A new problem is inserted as OPEN. A repeat of an OPEN issue only updates
     * the message and the count and keeps `readTime`. A repeat of a RESOLVED issue reopens it as unread.
     */
    suspend fun record(code: IssueCode, key: String, message: String, causeType: String?, actor: String)

    /** Resolves the OPEN issue of [code] and [key]. Returns false when there is none, which is the normal case. */
    suspend fun resolve(code: IssueCode, key: String, actor: String): Boolean

    suspend fun findByCodeAndKey(code: IssueCode, key: String): Issue?
}
