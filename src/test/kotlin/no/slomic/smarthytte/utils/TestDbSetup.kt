package no.slomic.smarthytte.utils

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

/**
 * This class is created to make it easier creating tests with an in-memory Sqlite database for testing Repositories and
 * database access. Since the Sqlite is closed after each transaction by the Exposed library, we need to do a
 * workaround to keep the database connection open between the beforeTest and the Test cases.
 * Read more at https://github.com/JetBrains/Exposed/issues/454
 */
class TestDbSetup {
    val sqlitePath = "jdbc:sqlite:file:test?mode=memory&cache=shared"
    lateinit var keepAliveConnection: Connection
    lateinit var flyway: Flyway

    // Call this in beforeTest in your spec
    fun setupDb() {
        /*
            Set UTC as the default timezone in tests to avoid inconsistency between the local dev environment and the
            test environment in GitHub.
            Exposed v0.59 introduced some bugs with handling timezone for timestamp types,
            see https://youtrack.jetbrains.com/issue/EXPOSED-731/Timestamp-support-for-SQLite-is-broken
         */
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val dataSource = SQLiteDataSource().apply {
            url = sqlitePath
            config.enforceForeignKeys(true)
        }

        keepAliveConnection = DriverManager.getConnection(sqlitePath)
        flyway = Flyway.configure()
            .dataSource(dataSource)
            .validateMigrationNaming(true)
            .cleanDisabled(false)
            .load()
        flyway.clean()
        flyway.migrate()
        Database.connect(dataSource)
    }

    // Call this in afterTest in your spec
    fun teardownDb() {
        flyway.clean()
        keepAliveConnection.close()
    }
}
