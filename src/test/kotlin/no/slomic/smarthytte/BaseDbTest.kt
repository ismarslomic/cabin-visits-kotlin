package no.slomic.smarthytte

import io.kotest.core.spec.style.StringSpec
import no.slomic.smarthytte.calendar.CalendarEventTable
import no.slomic.smarthytte.calendar.CalendarSyncTable
import no.slomic.smarthytte.checkin.CheckInSyncTable
import no.slomic.smarthytte.checkin.CheckInTable
import no.slomic.smarthytte.eventguest.CalenderEventGuestTable
import no.slomic.smarthytte.guest.GuestTable
import no.slomic.smarthytte.vehicletrip.VehicleTripTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager

/**
 * This class is created to make it easier creating tests with an in-memory Sqlite database for testing Repositories and
 * database access. Since the Sqlite is closed after each transaction by the Exposed library, we need to do a
 * workaround to keep the database connection open between the beforeTest and the Test cases.
 * Read more at https://github.com/JetBrains/Exposed/issues/454
 */
abstract class BaseDbTest(body: BaseDbTest.() -> Unit = {}) :
    StringSpec({
        val sqlitePath = "jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on"
        lateinit var keepAliveConnection: Connection

        beforeTest {
            keepAliveConnection = DriverManager.getConnection(sqlitePath)
            Database.connect(sqlitePath)
            transaction {
                SchemaUtils.create(
                    CalendarSyncTable,
                    CalendarEventTable,
                    GuestTable,
                    CalenderEventGuestTable,
                    VehicleTripTable,
                    CheckInSyncTable,
                    CheckInTable,
                )
            }
        }

        afterTest {
            transaction {
                SchemaUtils.drop(
                    CalendarSyncTable,
                    CalendarEventTable,
                    GuestTable,
                    CalenderEventGuestTable,
                    VehicleTripTable,
                    CheckInSyncTable,
                    CheckInTable,
                )
            }

            keepAliveConnection.close()
        }
    }) {
    init {
        body()
    }
}
