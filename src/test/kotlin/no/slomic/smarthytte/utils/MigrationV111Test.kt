@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.sql.Connection

/**
 * V1.11 rebuilds `vehicle_trip` with nullable energy, address and city and the new JLR columns. The links in
 * `reservation_vehicle_trip` must survive, because dropping the old table would cascade-delete them.
 */
class MigrationV111Test :
    StringSpec({
        // Prepared statements, see MigrationV19Test
        @Suppress("SqlSourceToSinkFlow")
        fun Connection.execute(sql: String) = prepareStatement(sql).use { it.execute() }

        @Suppress("SqlSourceToSinkFlow")
        fun <T> Connection.query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
            prepareStatement(sql).use { st ->
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
            }

        fun Connection.insertTrip(id: String) = execute(
            """
            insert into vehicle_trip (created_time, id, avg_energy_consumption, avg_energy_consumption_unit, avg_speed,
                distance, distance_unit, duration, duration_unit, end_address, end_city, end_time, energy_regenerated,
                energy_generated_unit, speed_unit, start_address, start_city, start_time, total_distance, created_by)
            values ('2025-01-01T00:00:00Z', '$id', 20.5, 'kWh/100km', 80.0, 100.0, 'km', 3600000000000, 'min',
                'End street 1', 'Ullsåk', '2025-03-01 12:00:00.000', 1.5, 'kWh', 'kmh', 'Start street 1', 'Oslo',
                '2025-03-01 10:00:00.000', 100.0, 'system')
            """.trimIndent(),
        )

        "should keep trips and the links to reservations, and mark old trips as complete" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.10")
                migration.withConnection { c ->
                    c.insertTrip("t1")
                    c.insertTrip("t2")
                    c.execute(
                        """
                        insert into reservation (created_time, id, start_time, end_time, created_by)
                        values ('2025-01-01T00:00:00Z', 'r1', '2025-03-01T10:00:00Z', '2025-03-02T10:00:00Z', 'system')
                        """.trimIndent(),
                    )
                    c.execute(
                        "insert into reservation_vehicle_trip (reservation_id, vehicle_trip_id, trip_type) values ('r1', 't1', 'TO_CABIN')",
                    )
                    c.execute(
                        "insert into reservation_vehicle_trip (reservation_id, vehicle_trip_id, trip_type) values ('r1', 't2', 'FROM_CABIN')",
                    )
                }

                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("select id, data_quality, jlr_journey_id, start_latitude from vehicle_trip order by id") {
                        listOf(
                            it.getString("id"),
                            it.getString("data_quality"),
                            it.getString("jlr_journey_id"),
                            it.getString("start_latitude"),
                        )
                    } shouldBe listOf(listOf("t1", "COMPLETE", null, null), listOf("t2", "COMPLETE", null, null))
                    c.query(
                        "select avg_energy_consumption, end_city, duration, created_by from vehicle_trip where id = 't1'",
                    ) {
                        listOf(it.getDouble(1), it.getString(2), it.getLong(3), it.getString(4))
                    } shouldBe listOf(listOf(20.5, "Ullsåk", 3_600_000_000_000L, "system"))
                    c.query(
                        "select vehicle_trip_id, trip_type from reservation_vehicle_trip order by vehicle_trip_id",
                    ) {
                        it.getString(1) to it.getString(2)
                    } shouldBe listOf("t1" to "TO_CABIN", "t2" to "FROM_CABIN")
                    c.query("pragma foreign_key_check") { it.getString(1) } shouldBe emptyList()
                }
            } finally {
                migration.delete()
            }
        }

        "should accept a trip without energy, address and city, and enforce the new constraints" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("pragma table_info(vehicle_trip)") { it.getString("name") } shouldContainAll
                        listOf(
                            "data_quality",
                            "jlr_journey_id",
                            "start_latitude",
                            "start_longitude",
                            "end_latitude",
                            "end_longitude",
                        )
                    val insertWithoutPlaces = """
                        insert into vehicle_trip (created_time, id, avg_speed, distance, distance_unit, duration,
                            duration_unit, end_time, speed_unit, start_time, total_distance, created_by, data_quality, jlr_journey_id)
                        values ('2026-11-01T00:00:00Z', '%s', 80.0, 10.0, 'km', 600000000000, 'min',
                            '2026-11-01 12:00:00.000', 'kmh', '2026-11-01 11:50:00.000', 10.0, 'system', '%s', '%s')
                    """.trimIndent()
                    c.execute(insertWithoutPlaces.format("n1", "PLACE_MISSING", "j1"))

                    // Same JLR journey id twice, and an unknown quality, are both rejected
                    runCatching { c.execute(insertWithoutPlaces.format("n2", "COMPLETE", "j1")) }.isFailure shouldBe
                        true
                    runCatching { c.execute(insertWithoutPlaces.format("n3", "UNKNOWN", "j3")) }.isFailure shouldBe true
                    c.query("select id from vehicle_trip") { it.getString(1) } shouldBe listOf("n1")
                }
            } finally {
                migration.delete()
            }
        }
    })
