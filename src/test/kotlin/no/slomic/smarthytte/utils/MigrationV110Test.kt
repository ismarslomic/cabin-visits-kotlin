@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.sql.Connection

/** V1.10 adds the nullable stay dates to `reservation_guest` and leaves every existing link as it is. */
class MigrationV110Test :
    StringSpec({
        // Prepared statements, see MigrationV19Test
        @Suppress("SqlSourceToSinkFlow")
        fun Connection.execute(sql: String) = prepareStatement(sql).use { it.execute() }

        @Suppress("SqlSourceToSinkFlow")
        fun <T> Connection.query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
            prepareStatement(sql).use { st ->
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
            }

        "should keep the existing links and give them no own dates" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.9")
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
                        values ('2025-01-01T00:00:00Z', 'r1', '2025-03-01T10:00:00Z', '2025-03-02T10:00:00Z', 'system')
                        """.trimIndent(),
                    )
                    c.execute("insert into reservation_guest (reservation_id, guest_id) values ('r1', 'ola.nordmann')")
                }

                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("pragma table_info(reservation_guest)") { it.getString("name") } shouldContainAll
                        listOf("reservation_id", "guest_id", "from_date", "to_date")
                    c.query("select guest_id, from_date, to_date from reservation_guest") {
                        Triple(it.getString("guest_id"), it.getString("from_date"), it.getString("to_date"))
                    } shouldBe listOf(Triple("ola.nordmann", null, null))
                    c.query("pragma foreign_key_check") { it.getString(1) } shouldBe emptyList()
                }
            } finally {
                migration.delete()
            }
        }
    })
