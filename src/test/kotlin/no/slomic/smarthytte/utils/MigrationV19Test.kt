@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.SQLException

/** V1.9 adds the `calendar_outbox` table and leaves every existing row as it is. */
class MigrationV19Test :
    StringSpec({
        // Prepared statements: a value is bound as a parameter, never concatenated into the SQL. IntelliJ flags every
        // non-constant string that reaches a statement, here the fixed SQL text of these tests.
        @Suppress("SqlSourceToSinkFlow")
        fun Connection.execute(sql: String, vararg parameters: String) = prepareStatement(sql).use { st ->
            parameters.forEachIndexed { index, value -> st.setString(index + 1, value) }
            st.execute()
        }

        @Suppress("SqlSourceToSinkFlow")
        fun <T> Connection.query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
            prepareStatement(sql).use { st ->
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
            }

        "should create the outbox and keep the reservations, guests and links" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.8")
                migration.withConnection { c ->
                    c.execute(
                        """
                        insert into guest (created_time, id, first_name, last_name, birth_year, gender, created_by)
                        values ('2025-01-01T00:00:00Z', 'ola.nordmann', 'Ola', 'Nordmann', 1980, 'MALE', 'system')
                        """.trimIndent(),
                    )
                    c.execute(
                        """
                        insert into reservation (created_time, id, start_time, end_time, created_by)
                        values ('2025-01-01T00:00:00Z', '_legacy', '2025-03-01T10:00:00Z', '2025-03-02T10:00:00Z', 'system')
                        """.trimIndent(),
                    )
                    c.execute(
                        "insert into reservation_guest (reservation_id, guest_id) values ('_legacy', 'ola.nordmann')",
                    )
                }

                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("select count(*) from reservation") { it.getInt(1) } shouldBe listOf(1)
                    c.query("select count(*) from reservation_guest") { it.getInt(1) } shouldBe listOf(1)
                    c.query("select count(*) from calendar_outbox") { it.getInt(1) } shouldBe listOf(0)
                    c.query("pragma table_info(calendar_outbox)") { it.getString("name") } shouldContainAll
                        listOf(
                            "id",
                            "reservation_id",
                            "operation",
                            "created_time",
                            "attempts",
                            "last_error",
                            "next_attempt_time",
                        )
                    c.query("pragma foreign_key_check") { it.getString(1) } shouldBe emptyList()
                }
            } finally {
                migration.delete()
            }
        }

        "should let an outbox row outlive its reservation, count attempts from zero and generate the id" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateToLatest()

                migration.withConnection { c ->
                    // No foreign key: a DELETE row refers to a reservation that no longer exists
                    c.execute(
                        """
                        insert into calendar_outbox (reservation_id, operation, created_time, next_attempt_time)
                        values ('gone', 'DELETE', '2026-07-10T10:00:00Z', '2026-07-10T10:00:00Z'),
                               ('gone', 'INSERT', '2026-07-10T10:00:01Z', '2026-07-10T10:00:01Z')
                        """.trimIndent(),
                    )

                    c.query("select id, attempts, last_error from calendar_outbox order by id") {
                        Triple(it.getInt("id"), it.getInt("attempts"), it.getString("last_error"))
                    } shouldBe listOf(Triple(1, 0, null), Triple(2, 0, null))
                }
            } finally {
                migration.delete()
            }
        }

        "should only accept the operations insert, update and delete" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateToLatest()

                migration.withConnection { c ->
                    fun insert(operation: String) = c.execute(
                        "insert into calendar_outbox (reservation_id, operation, created_time, next_attempt_time) " +
                            "values ('r', ?, '2026-07-10T10:00:00Z', '2026-07-10T10:00:00Z')",
                        operation,
                    )

                    listOf("INSERT", "UPDATE", "DELETE").forEach { insert(it) }
                    shouldThrow<SQLException> { insert("PATCH") }
                }
            } finally {
                migration.delete()
            }
        }
    })
