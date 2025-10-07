package no.slomic.smarthytte.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.log
import no.slomic.smarthytte.properties.DatabasePropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.sqlite.SQLiteDataSource
import java.sql.Connection

fun Application.configureDatabases() {
    val databaseProperties = loadProperties<DatabasePropertiesHolder>().database
    val databaseFilePath = databaseProperties.filePath
    val dataSource = SQLiteDataSource().apply {
        url = "jdbc:sqlite:$databaseFilePath"
        // We must enable the foreign keys constraints to enable on delete actions in Sqlite
        // read more at https://www.sqlite.org/foreignkeys.html#fk_actions
        config.enforceForeignKeys(true)
    }

    log.info("Initializing database..")

    // Use flyway to migrate database structure according to scripts located in src/main/resources/db/migration
    Flyway
        .configure()
        .dataSource(dataSource)
        .validateMigrationNaming(true)
        .load()
        .migrate()

    // Make an Exposed connection to the database
    Database.connect(datasource = dataSource)

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    log.info("Database initialization complete")
}
