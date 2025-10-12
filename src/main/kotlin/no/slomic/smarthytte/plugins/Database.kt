package no.slomic.smarthytte.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.log
import no.slomic.smarthytte.properties.DatabasePropertiesHolder
import no.slomic.smarthytte.properties.loadProperties
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.Logger
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection

@Suppress("SpreadOperator")
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

    val migrationLocations = flywayMigrationLocations(Thread.currentThread().contextClassLoader, log)

    Flyway
        .configure()
        .dataSource(dataSource)
        .locations(migrationLocations)
        .validateMigrationNaming(true)
        .load()
        .migrate()

    // Make an Exposed connection to the database
    Database.connect(datasource = dataSource)

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    log.info("Database initialization complete")
}

/**
 * In native-image we avoid classpath directory scanning by
 * providing a custom ResourceProvider that reads db/migration-index.txt (generated at build time).
 * This limitation was addressed many years ago in Flyway issues: https://github.com/flyway/flyway/issues/2927 and
 * ttps://github.com/flyway/flyway/issues/3509
 */
private fun flywayMigrationLocations(classLoader: ClassLoader, log: Logger): String {
    val migrationIndexFileName = "db/migration-index.txt"

    val defaultMigrationLocation = "classpath:db/migration"

    val migrationFilePaths: List<String> = run {
        val stream = classLoader.getResourceAsStream(migrationIndexFileName)
        if (stream != null) {
            log.info("Migration index file $migrationIndexFileName was found")
            stream.bufferedReader().use { br ->
                br.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        } else {
            log.error("Migration index file $migrationIndexFileName was not found, returning empty list")
            emptyList()
        }
    }

    // If non-empty list of migration files, extract the listed migrations to a temp directory
    // and point Flyway to that filesystem location.
    if (migrationFilePaths.isNotEmpty()) {
        val tmpDir = prepareMigrationTempDir(log)
        // copy migration files to the temp directory (cleaned/created on startup)
        copyMigrationScriptsToTempDir(migrationFilePaths, classLoader, tmpDir, log)
        val filesystemLocation = "filesystem:${tmpDir.absolutePath}"
        log.info(
            "Flyway migration configured with location $filesystemLocation (file count: ${migrationFilePaths.size})",
        )
        return filesystemLocation
    } else {
        // Fall back to the default classpath location
        log.info(
            "Flyway migration configured with default location: $defaultMigrationLocation (no migration index found)",
        )
        return defaultMigrationLocation
    }
}

private fun copyMigrationScriptsToTempDir(
    migrationFilePaths: List<String>,
    classLoader: ClassLoader,
    tmpDir: File,
    log: Logger,
) {
    migrationFilePaths.forEach { path ->
        val normalizedFilePath = path.removePrefix("/")
        val inStream = classLoader.getResourceAsStream(normalizedFilePath)
        if (inStream != null) {
            val outFile = File(tmpDir, normalizedFilePath.substringAfterLast('/'))
            outFile.outputStream().use { out -> inStream.copyTo(out) }
            log.info("Migration script at: $normalizedFilePath copied to temp directory: $outFile")
        } else {
            log.error("Migration script not found at: $normalizedFilePath")
        }
    }
}

/**
 * Creates a stable and deterministic temporary directory for Flyway migrations scripts.
 * If the directory already exists, it will be cleaned of any existing files.
 */
private fun prepareMigrationTempDir(log: Logger): File {
    val tempDirName = "cabin-visits-flyway"
    val baseTmp = System.getProperty("java.io.tmpdir")
    val dir = File(baseTmp, tempDirName)
    if (dir.exists()) {
        // Best-effort cleanup from any previous unclean shutdown
        dir.deleteRecursively()
    }
    if (!dir.mkdirs() && !dir.exists()) {
        error("Failed to create Flyway migration temp directory: ${dir.absolutePath}")
    }
    log.info("Using stable temp directory for Flyway migrations at: ${dir.absolutePath}")
    return dir
}
