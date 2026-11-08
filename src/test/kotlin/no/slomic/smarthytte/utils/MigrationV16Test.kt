package no.slomic.smarthytte.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/** V1.6 removes the `guest.api_edited_fields` column and the Google Calendar sync checkpoint. */
class MigrationV16Test :
    StringSpec({
        fun MigrationTestSupport.columnsOfGuest(): List<String> = withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select name from pragma_table_info('guest')").use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }

        "should drop api_edited_fields, keep the guest values and remove only the Google Calendar checkpoint" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.5")
                migration.withConnection { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            insert into guest (created_time, id, first_name, last_name, birth_year, gender, role, api_edited_fields)
                            values ('2025-01-01T00:00:00Z', 'ola-nordmann', 'Johnny', 'Nordmann', 1980, 'MALE', 'ADMIN', 'FIRST_NAME,ROLE')
                            """.trimIndent(),
                        )
                        statement.execute(
                            """
                            insert into sync_checkpoint (created_time, id, checkpoint_value) values
                            ('2025-01-01T00:00:00Z', 'google_calendar_events', 'sync-token'),
                            ('2025-01-01T00:00:00Z', 'vehicle_trips', '2025-01-01'),
                            ('2025-01-01T00:00:00Z', 'check_in_out_sensor', '2025-01-01T00:00:00Z')
                            """.trimIndent(),
                        )
                    }
                }
                migration.columnsOfGuest() shouldContain "api_edited_fields"

                migration.migrateTo("1.6")

                migration.columnsOfGuest() shouldNotContain "api_edited_fields"
                migration.withConnection { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("select first_name, role from guest where id = 'ola-nordmann'").use {
                            it.next() shouldBe true
                            it.getString("first_name") shouldBe "Johnny"
                            it.getString("role") shouldBe "ADMIN"
                        }
                        statement.executeQuery("select id from sync_checkpoint order by id").use {
                            val ids = buildList { while (it.next()) add(it.getString(1)) }
                            ids shouldBe listOf("check_in_out_sensor", "vehicle_trips")
                        }
                        statement.executeQuery("pragma foreign_key_check").use { it.next() shouldBe false }
                    }
                }
            } finally {
                migration.delete()
            }
        }

        "should run on a database without any guests or checkpoints" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.5")
                migration.migrateTo("1.6")

                migration.columnsOfGuest() shouldNotContain "api_edited_fields"
            } finally {
                migration.delete()
            }
        }
    })
