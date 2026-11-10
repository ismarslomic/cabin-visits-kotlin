package no.slomic.smarthytte.plugins

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.testApplication
import no.slomic.smarthytte.auth.testGuest
import no.slomic.smarthytte.calendar.CalendarOutboxTable
import no.slomic.smarthytte.common.ReadDatabase
import no.slomic.smarthytte.guests.SqliteGuestRepository
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path

// Plain functions, not inline in the suspend test body: blocking calls there are flagged as thread starvation risks
private fun createDatabaseFile(): Path = Files.createTempFile("configure-databases-test", ".db")

private fun deleteDatabaseFile(file: Path) {
    Files.deleteIfExists(file)
}

private fun outboxRowCount(): Long = transaction { CalendarOutboxTable.selectAll().count() }

class DatabaseTest :
    StringSpec({
        afterTest {
            // The next spec connects its own database; a stale default would point at the deleted file
            ReadDatabase.database = null
            TransactionManager.defaultDatabase = null
        }

        "configureDatabases should migrate the file and connect a write database and a separate read database" {
            val file = createDatabaseFile()
            try {
                testApplication {
                    application { configureDatabases(file.toString()) }
                    startApplication()

                    val read = ReadDatabase.database.shouldNotBeNull()
                    read shouldNotBe TransactionManager.defaultDatabase
                    // Migrated to the latest version, so the outbox table exists
                    outboxRowCount() shouldBe 0L

                    // A write through the default database is visible to a read through the read database
                    val repository = SqliteGuestRepository()
                    repository.addOrUpdate(testGuest("a"))
                    repository.guestById("a").shouldNotBeNull()
                }
            } finally {
                deleteDatabaseFile(file)
            }
        }
    })
