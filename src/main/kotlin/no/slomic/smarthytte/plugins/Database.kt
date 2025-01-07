package no.slomic.smarthytte.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.log
import no.slomic.smarthytte.calendar.CalendarEventTable
import no.slomic.smarthytte.eventguest.CalenderEventGuestTable
import no.slomic.smarthytte.guest.GuestTable
import no.slomic.smarthytte.properties.DatabasePropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

fun Application.configureDatabases() {
    val databaseProperties = loadProperties<DatabasePropertiesHolder>().database
    val databaseFilePath = databaseProperties.filePath

    log.info("Initializing database..")

    Database.connect(
        // We must enable the foreign keys constraints to enable on delete actions in Sqlite
        // read more at https://www.sqlite.org/foreignkeys.html#fk_actions
        url = "jdbc:sqlite:$databaseFilePath?foreign_keys=on",
        driver = "org.sqlite.JDBC",
    )

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    // Initialize schemas
    transaction {
        // Create tables that do not exist
        SchemaUtils.create(CalendarEventTable, GuestTable, CalenderEventGuestTable)

        // Create missing columns to existing tables if changed from a previous app version
        SchemaUtils.createMissingTablesAndColumns(CalendarEventTable, GuestTable, CalenderEventGuestTable)

        // Removing already created columns is not supported directly by Exposed. We need to execute the raw sql or use
        // db migration library such as Flyway or Liquibase.
        // exec("ALTER TABLE calendar_event DROP COLUMN test_column")
    }

    log.info("Database initialization complete")
}
