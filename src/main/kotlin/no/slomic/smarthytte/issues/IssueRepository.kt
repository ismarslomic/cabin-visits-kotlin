package no.slomic.smarthytte.issues

/** What [IssueRepository.setRead] did. */
sealed interface IssueReadOutcome {
    data object Updated : IssueReadOutcome

    /** The issue already had the wanted read state, so nothing changed and the version stays. */
    data object Unchanged : IssueReadOutcome
    data object NotFound : IssueReadOutcome
    data object VersionMismatch : IssueReadOutcome
}

interface IssueRepository {
    /**
     * Stores an occurrence of the problem. A new problem is inserted as OPEN. A repeat of an OPEN issue only updates
     * the message and the count and keeps `readTime`. A repeat of a RESOLVED issue reopens it as unread.
     */
    suspend fun record(code: IssueCode, key: String, message: String, causeType: String?, actor: String)

    /** Resolves the OPEN issue of [code] and [key]. Returns false when there is none, which is the normal case. */
    suspend fun resolve(code: IssueCode, key: String, actor: String): Boolean

    suspend fun findByCodeAndKey(code: IssueCode, key: String): Issue?

    suspend fun findById(id: Long): Issue?

    /** The issues that match the filters (null means any), the latest occurrence first, at most [limit]. */
    suspend fun list(isRead: Boolean?, status: IssueStatus?, limit: Int): List<Issue>

    /** The OPEN issues that are not read. */
    suspend fun unreadCount(): Int

    /**
     * Marks the issue as read or unread if [expectedVersion] is its version. A change of the read state bumps the
     * version, a request for the state it already has does not.
     */
    suspend fun setRead(id: Long, expectedVersion: Short, read: Boolean, actor: String): IssueReadOutcome
}
