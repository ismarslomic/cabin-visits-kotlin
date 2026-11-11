package no.slomic.smarthytte.calendar

import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicLong

/**
 * The state of the outbox as Prometheus gauges. The drain updates the values after every run, so a scrape never has to
 * read the database.
 */
class CalendarOutboxMetrics {
    private val pending = AtomicLong()
    private val oldestAgeSeconds = AtomicLong()

    fun update(pending: Long, oldestAgeSeconds: Long) {
        this.pending.set(pending)
        this.oldestAgeSeconds.set(oldestAgeSeconds)
    }

    fun bind(registry: MeterRegistry) {
        registry.gauge("calendar_outbox_pending", pending)
        registry.gauge("calendar_outbox_oldest_age_seconds", oldestAgeSeconds)
    }
}
