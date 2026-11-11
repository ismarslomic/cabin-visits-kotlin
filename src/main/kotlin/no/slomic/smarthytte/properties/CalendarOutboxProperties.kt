package no.slomic.smarthytte.properties

data class CalendarOutboxPropertiesHolder(val calendarOutbox: CalendarOutboxProperties)

/**
 * Settings of the push of reservation changes to Google Calendar. All have a default in `application.yml`, so the
 * variables are optional. No Kotlin defaults here: the native image reflection entry is a plain constructor.
 */
data class CalendarOutboxProperties(
    /** Title of every event the app creates. Old events keep their own title. */
    val eventTitle: String,
    /** The most outbox rows one drain reads. */
    val batchSize: Int,
    /** Seconds between two drains. */
    val drainIntervalSeconds: Long,
    /** Seconds to wait after the first failed push of a row. */
    val backoffStartSeconds: Long,
    /** Each further failure waits this many times longer. */
    val backoffFactor: Double,
    /** The longest wait between two pushes of a row, also used for a quota error. */
    val backoffCapSeconds: Long,
    /** A row older than this many minutes is logged as stuck and shows in the metrics. */
    val stuckThresholdMinutes: Long,
)
