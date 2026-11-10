package no.slomic.smarthytte.utils

import no.slomic.smarthytte.common.ReadDatabase
import no.slomic.smarthytte.plugins.connectDatabase
import no.slomic.smarthytte.plugins.connectReadDatabase
import no.slomic.smarthytte.plugins.createSqliteDataSource
import no.slomic.smarthytte.plugins.createSqliteReadDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.nio.file.Files

/**
 * The real database setup of the application on a temp file: the same `DataSource` and Exposed configuration as
 * production, migrated to the latest version. Use it for tests that depend on how SQLite locks a file database
 * (concurrent writers), which the shared-cache in-memory database of [TestDbSetup] does not reproduce.
 */
class FileDatabaseTestSupport {
    private val databaseFile = Files.createTempFile("file-database-test", ".db")

    fun setup() {
        val dataSource = createSqliteDataSource("jdbc:sqlite:$databaseFile")

        Flyway.configure().dataSource(dataSource).validateMigrationNaming(true).load().migrate()
        connectDatabase(dataSource)
        connectReadDatabase(createSqliteReadDataSource("jdbc:sqlite:$databaseFile"))
    }

    fun delete() {
        // The next spec connects its own database; a stale default would point at the deleted file
        ReadDatabase.database = null
        TransactionManager.defaultDatabase = null
        Files.deleteIfExists(databaseFile)
    }
}
