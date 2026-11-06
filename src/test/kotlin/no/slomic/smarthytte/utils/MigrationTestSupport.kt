package no.slomic.smarthytte.utils

import org.flywaydb.core.Flyway
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.sql.Connection
import javax.sql.DataSource

/**
 * A database of its own in a temp file, so a migration can be tested on top of an older schema version.
 *
 * [TestDbSetup] cannot be used for this: it always migrates to the latest version on a shared in-memory database.
 */
class MigrationTestSupport {
    private val databaseFile = Files.createTempFile("migration-test", ".db")
    private val dataSource: DataSource = SQLiteDataSource().apply {
        url = "jdbc:sqlite:$databaseFile"
        config.enforceForeignKeys(true)
    }

    /** Migrates up to and including [version], e.g. "1.5". */
    fun migrateTo(version: String) {
        flyway().target(version).load().migrate()
    }

    fun migrateToLatest() {
        flyway().load().migrate()
    }

    fun <T> withConnection(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun delete() {
        Files.deleteIfExists(databaseFile)
    }

    private fun flyway() = Flyway.configure().dataSource(dataSource).validateMigrationNaming(true)
}
