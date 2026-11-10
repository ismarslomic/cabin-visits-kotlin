package no.slomic.smarthytte.plugins

import no.slomic.smarthytte.common.ReadDatabase
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.sqlite.SQLiteConfig.TransactionMode
import org.sqlite.SQLiteDataSource
import java.sql.Connection

/** How long a connection waits for the write lock of another connection before it fails with SQLITE_BUSY. */
const val BUSY_TIMEOUT_MS = 5_000

/**
 * The SQLite `DataSource` for writes, shared by `configureDatabases` and the tests that need a file database
 * (the in-memory database of `TestDbSetup` does not lock like a file does).
 */
fun createSqliteDataSource(url: String): SQLiteDataSource = createSqliteDataSource(url, TransactionMode.IMMEDIATE)

/**
 * The SQLite `DataSource` for reads: the default deferred `BEGIN`, which takes only a read lock, so reads run
 * concurrently with each other and with a writer. Reads that went through the write `DataSource` queued behind each
 * other on the write lock (50 concurrent reads took up to 4 s instead of 0.13 s).
 */
fun createSqliteReadDataSource(url: String): SQLiteDataSource = createSqliteDataSource(url, TransactionMode.DEFERRED)

private fun createSqliteDataSource(url: String, mode: TransactionMode): SQLiteDataSource = SQLiteDataSource().apply {
    this.url = url
    // We must enable the foreign keys constraints to enable on delete actions in Sqlite
    // read more at https://www.sqlite.org/foreignkeys.html#fk_actions
    config.enforceForeignKeys(true)
    // BEGIN IMMEDIATE takes the write lock when a transaction starts, so concurrent writers queue behind the busy
    // timeout instead of failing with SQLITE_BUSY when they try to upgrade a read lock (see decision 58 in the plan)
    config.transactionMode = mode
    config.busyTimeout = BUSY_TIMEOUT_MS
}

/**
 * Connects Exposed to the write [dataSource] with the transaction settings of the application and makes it the default
 * database, the one `suspendTransaction` writes to.
 */
fun connectDatabase(dataSource: SQLiteDataSource): Database = connect(dataSource).also {
    TransactionManager.defaultDatabase = it
}

/** Connects Exposed to the read [dataSource] and makes it the database of `suspendReadTransaction`. */
fun connectReadDatabase(dataSource: SQLiteDataSource): Database = connect(dataSource).also {
    ReadDatabase.database = it
}

private fun connect(dataSource: SQLiteDataSource): Database = Database.connect(
    datasource = dataSource,
    databaseConfig = DatabaseConfig {
        defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    },
)
