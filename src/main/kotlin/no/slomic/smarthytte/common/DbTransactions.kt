package no.slomic.smarthytte.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** A read-write transaction on the default (write) database. */
suspend fun <T> suspendTransaction(block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
    transaction { block() }
}

/**
 * The database that [suspendReadTransaction] reads from: its own connections, which do not take the write lock. Set
 * by `configureDatabases`; null, as in the in-memory database of most tests, means reads use the default database.
 */
object ReadDatabase {
    @Volatile
    var database: Database? = null
}

/** A transaction that only reads. It never waits for, and never takes, the write lock that writers queue on. */
suspend fun <T> suspendReadTransaction(block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
    transaction(ReadDatabase.database) { block() }
}
