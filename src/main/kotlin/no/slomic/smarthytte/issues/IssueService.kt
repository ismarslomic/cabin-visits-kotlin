package no.slomic.smarthytte.issues

const val DEFAULT_ISSUE_LIMIT = 100
const val MAX_ISSUE_LIMIT = 500

sealed interface IssueListResult {
    data class Listed(val issues: List<Issue>) : IssueListResult
    data class Invalid(val message: String) : IssueListResult
}

sealed interface IssueUpdateResult {
    data class Updated(val issue: Issue) : IssueUpdateResult
    data object NotFound : IssueUpdateResult
    data object VersionMismatch : IssueUpdateResult
}

/** What the admin does with issues: list them, count the unread ones and mark them as read. */
class IssueService(private val issueRepository: IssueRepository) {
    /** The filters are the raw query parameters, so an invalid one is answered here as [IssueListResult.Invalid]. */
    suspend fun list(read: String?, status: String?, limit: String?): IssueListResult {
        val filter = try {
            IssueFilter.parse(read, status, limit)
        } catch (e: IllegalArgumentException) {
            return IssueListResult.Invalid(e.message.orEmpty())
        }

        return IssueListResult.Listed(issueRepository.list(filter.isRead, filter.status, filter.limit))
    }

    suspend fun unreadCount(): Int = issueRepository.unreadCount()

    suspend fun markRead(actorId: String, id: Long, update: IssueUpdate): IssueUpdateResult =
        when (issueRepository.setRead(id, update.version, update.read, actorId)) {
            IssueReadOutcome.NotFound -> IssueUpdateResult.NotFound

            IssueReadOutcome.VersionMismatch -> IssueUpdateResult.VersionMismatch

            IssueReadOutcome.Updated, IssueReadOutcome.Unchanged ->
                issueRepository.findById(id)?.let(IssueUpdateResult::Updated) ?: IssueUpdateResult.NotFound
        }
}

private class IssueFilter(val isRead: Boolean?, val status: IssueStatus?, val limit: Int) {
    companion object {
        /** @throws IllegalArgumentException with the reason, when a parameter is not valid */
        fun parse(read: String?, status: String?, limit: String?) = IssueFilter(
            isRead = read?.let { requireNotNull(it.toBooleanStrictOrNull()) { "read must be true or false" } },
            status = status?.let { text ->
                requireNotNull(
                    IssueStatus.entries.firstOrNull {
                        it.name == text
                    },
                ) { "status must be OPEN or RESOLVED" }
            },
            limit = limit?.let { text ->
                requireNotNull(text.toIntOrNull()?.takeIf { it in 1..MAX_ISSUE_LIMIT }) {
                    "limit must be a number from 1 to $MAX_ISSUE_LIMIT"
                }
            } ?: DEFAULT_ISSUE_LIMIT,
        )
    }
}
