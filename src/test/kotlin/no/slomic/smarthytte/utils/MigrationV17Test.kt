@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.sql.Connection

/**
 * V1.7 adds `created_by` and `updated_by` to `reservation`, `guest`, `vehicle_trip`, `check_in_out_sensor` and
 * `sync_checkpoint`, and rebuilds `guest_avatar` (not null `created_time`, nullable `updated_time`, audit columns and
 * `version`).
 */
class MigrationV17Test :
    StringSpec({
        fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }

        fun <T> Connection.query(sql: String, read: (java.sql.ResultSet) -> T): List<T> = createStatement().use { st ->
            st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(read(rs)) } }
        }

        "should backfill the audit columns and rebuild guest_avatar without losing rows" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.6")
                migration.withConnection { c ->
                    c.execute(
                        """
                        insert into guest (created_time, updated_time, id, first_name, last_name, birth_year, gender)
                        values ('2025-01-01T00:00:00Z', '2025-02-01T00:00:00Z', 'edited', 'Ed', 'Ited', 1980, 'MALE'),
                               ('2025-01-01T00:00:00Z', null, 'untouched', 'Un', 'Touched', 1981, 'FEMALE')
                        """.trimIndent(),
                    )
                    c.execute(
                        """
                        insert into reservation (created_time, updated_time, id, start_time, end_time)
                        values ('2025-01-01T00:00:00Z', '2025-02-01T00:00:00Z', 'r1', '2025-03-01T10:00:00Z', '2025-03-02T10:00:00Z'),
                               ('2025-01-01T00:00:00Z', null, 'r2', '2025-04-01T10:00:00Z', '2025-04-02T10:00:00Z')
                        """.trimIndent(),
                    )
                    c.execute(
                        "insert into reservation_guest (reservation_id, guest_id) values ('r1', 'edited'), ('r2', 'untouched')",
                    )
                    c.execute(
                        """
                        insert into vehicle_trip (created_time, updated_time, id, avg_energy_consumption, avg_energy_consumption_unit,
                            avg_speed, distance, distance_unit, duration, duration_unit, end_address, end_city, end_time,
                            energy_regenerated, energy_generated_unit, speed_unit, start_address, start_city, start_time, total_distance)
                        values ('2025-01-01T00:00:00Z', null, 't1', 1, 'u', 1, 1, 'km', 1, 's', 'a', 'c', '2025-03-01T11:00:00Z', 1, 'k', 'k', 'a', 'c', '2025-03-01T10:00:00Z', 1),
                               ('2025-01-01T00:00:00Z', '2025-02-01T00:00:00Z', 't2', 1, 'u', 1, 1, 'km', 1, 's', 'a', 'c', '2025-03-01T11:00:00Z', 1, 'k', 'k', 'a', 'c', '2025-03-01T10:00:00Z', 1)
                        """.trimIndent(),
                    )
                    c.execute(
                        """
                        insert into check_in_out_sensor (created_time, updated_time, id, time, status)
                        values ('2025-01-01T00:00:00Z', '2025-02-01T00:00:00Z', 's1', '2025-01-01T00:00:00Z', 'CHECKED_IN'),
                               ('2025-01-01T00:00:00Z', null, 's2', '2025-01-02T00:00:00Z', 'CHECKED_OUT')
                        """.trimIndent(),
                    )
                    c.execute(
                        """
                        insert into sync_checkpoint (created_time, updated_time, id, checkpoint_value)
                        values ('2025-01-01T00:00:00Z', '2025-02-01T00:00:00Z', 'c1', 'v'),
                               ('2025-01-01T00:00:00Z', null, 'c2', 'v')
                        """.trimIndent(),
                    )
                    c.execute(
                        "insert into guest_avatar (guest_id, image, updated_time) values ('edited', x'010203', '2025-02-15T00:00:00Z')",
                    )
                }

                migration.migrateToLatest()

                migration.withConnection { c ->
                    for (table in listOf(
                        "guest",
                        "reservation",
                        "vehicle_trip",
                        "check_in_out_sensor",
                        "sync_checkpoint",
                    )) {
                        c.query(
                            "select created_by, updated_by, updated_time is null from $table order by updated_time is null",
                        ) {
                            Triple(it.getString(1), it.getString(2), it.getInt(3) == 1)
                        } shouldBe listOf(Triple("system", "system", false), Triple("system", null, true))
                    }

                    c.query(
                        "select guest_id, hex(image), created_time, created_by, updated_time, updated_by from guest_avatar",
                    ) {
                        listOf(1, 2, 3, 4, 5, 6).map { i -> it.getString(i) }
                    } shouldBe listOf(
                        listOf("edited", "010203", "2025-02-15T00:00:00Z", "system", "2025-02-15T00:00:00Z", "system"),
                    )

                    c.query("select version from guest_avatar") { it.getInt(1) } shouldBe listOf(1)
                    c.query("select count(*) from reservation_guest") { it.getInt(1) } shouldBe listOf(2)
                    c.query("pragma foreign_key_check") { it.getString(1) } shouldBe emptyList()
                    // The foreign key to guest (cascade) survives the rebuild
                    c.execute("delete from guest where id = 'edited'")
                    c.query("select count(*) from guest_avatar") { it.getInt(1) } shouldBe listOf(0)
                }
            } finally {
                migration.delete()
            }
        }

        "should run on a database without rows and keep the default of created_by" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.execute(
                        "insert into guest (created_time, id, first_name, last_name, birth_year, gender) " +
                            "values ('2025-01-01T00:00:00Z', 'g', 'G', 'G', 1980, 'MALE')",
                    )
                    c.query("select created_by, updated_by from guest") { it.getString(1) to it.getString(2) } shouldBe
                        listOf("system" to null)
                    // guest_avatar has no default: a forgotten actor fails loudly
                    runCatching {
                        c.execute(
                            "insert into guest_avatar (guest_id, image, created_time) values ('g', x'01', '2025-01-01T00:00:00Z')",
                        )
                    }.isFailure shouldBe true
                }
            } finally {
                migration.delete()
            }
        }
    })
