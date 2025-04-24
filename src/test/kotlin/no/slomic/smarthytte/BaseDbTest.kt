package no.slomic.smarthytte

import io.kotest.core.spec.style.StringSpec
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteDataSource
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
        val sqlitePath = "jdbc:sqlite:file:test?mode=memory&cache=shared"
        lateinit var keepAliveConnection: Connection
        lateinit var flyway: Flyway

        beforeTest {
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

        afterTest {
            flyway.clean()
            keepAliveConnection.close()
        }
    }) {
    init {
        body()
    }
}
