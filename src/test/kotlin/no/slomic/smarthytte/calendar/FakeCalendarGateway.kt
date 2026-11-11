package no.slomic.smarthytte.calendar

/** A [CalendarGateway] that records its calls and answers what the test scripts; success by default. */
class FakeCalendarGateway : CalendarGateway {
    /** `insert <id>`, `patch <id>`, `restore <id>` or `delete <id>`, in call order. */
    val calls = mutableListOf<String>()
    val insertedTimes = mutableMapOf<String, CalendarEventTimes>()

    var onInsert: suspend (String, CalendarEventTimes) -> CalendarCallResult = { _, _ -> CalendarCallResult.Success }
    var onPatch: suspend (String, CalendarEventTimes, Boolean) -> CalendarCallResult =
        { _, _, _ -> CalendarCallResult.Success }
    var onDelete: suspend (String) -> CalendarCallResult = { CalendarCallResult.Success }

    override suspend fun insert(eventId: String, times: CalendarEventTimes): CalendarCallResult {
        calls.add("insert $eventId")
        insertedTimes[eventId] = times
        return onInsert(eventId, times)
    }

    override suspend fun patch(eventId: String, times: CalendarEventTimes, restore: Boolean): CalendarCallResult {
        calls.add("${if (restore) "restore" else "patch"} $eventId")
        return onPatch(eventId, times, restore)
    }

    override suspend fun delete(eventId: String): CalendarCallResult {
        calls.add("delete $eventId")
        return onDelete(eventId)
    }
}

fun failure(status: Int?, reason: String? = null) = CalendarCallResult.Failure(status, reason, "failure $status")
