@file:Suppress("MaxLineLength")

package no.slomic.smarthytte.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.guests.guestSlug
import java.sql.Connection

/**
 * V1.8 renames the guest ids to `<firstname>.<lastname>` (the slug of `POST /api/guests`), moves the references in
 * `reservation_guest` and `guest_avatar`, and the guest ids in `created_by`/`updated_by`.
 */
private fun String.sqlEscaped() = replace("'", "''")

class MigrationV18Test :
    StringSpec({
        fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }

        fun <T> Connection.query(sql: String, read: (java.sql.ResultSet) -> T): List<T> = createStatement().use { st ->
            st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(read(rs)) } }
        }

        fun guestRow(id: String, first: String, last: String, email: String? = null, audit: String = "'system', null") =
            "('2025-01-01T00:00:00Z', ${if (audit.endsWith(
                    "null",
                )
            ) {
                "null"
            } else {
                "'2025-02-01T00:00:00Z'"
            }}, '$id', '$first', '$last', 1980, 'MALE', ${email?.let {
                "'$it'"
            } ?: "null"}, 'ADMIN', $audit, 3)"

        "should rename the guests, move the references and keep everything else" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.7")
                migration.withConnection { c ->
                    c.execute(
                        """
                        insert into guest (created_time, updated_time, id, first_name, last_name, birth_year, gender, email, role, created_by, updated_by, version)
                        values ${guestRow("ola", "Ola", "Nordmann", "Ola@Example.com", "'system', 'ola'")},
                               ${guestRow("orjan", "Ørjan", "Johansen")},
                               ${guestRow("fredrik", "Fredrik", "Bjørnøy", "fredrik@example.com")},
                               ${guestRow("kjell", "Kjell Olav", "Dale")},
                               ${guestRow("anna1", "Anna", "Berg")},
                               ${guestRow("anna2", "Anna", "Berg", "anna@example.com")},
                               ${guestRow("jean", "Jean-Luc", "O''Brien")},
                               ${guestRow("hansen2", "Kari", "Hansen")},
                               ${guestRow("kari.hansen", "Kari", "Hansen")}
                        """.trimIndent(),
                    )
                    c.execute(
                        """
                        insert into reservation (created_time, updated_time, id, start_time, end_time, created_by, updated_by)
                        values ('2025-01-01T00:00:00Z', '2025-02-01T00:00:00Z', 'r1', '2025-03-01T10:00:00Z', '2025-03-02T10:00:00Z', 'orjan', 'ola'),
                               ('2025-01-01T00:00:00Z', null, 'r2', '2025-04-01T10:00:00Z', '2025-04-02T10:00:00Z', 'system', null)
                        """.trimIndent(),
                    )
                    c.execute(
                        "insert into reservation_guest (reservation_id, guest_id) values ('r1', 'ola'), ('r1', 'anna2'), ('r2', 'anna1'), ('r2', 'kari.hansen')",
                    )
                    c.execute(
                        "insert into guest_avatar (guest_id, image, created_time, created_by, updated_time, updated_by, version) values ('ola', x'010203', '2025-01-01T00:00:00Z', 'ola', '2025-02-01T00:00:00Z', 'orjan', 2)",
                    )
                }

                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("select id, first_name, email, version, created_by, updated_by from guest order by id") {
                        listOf(
                            it.getString(1),
                            it.getString(2),
                            it.getString(3),
                            it.getInt(4),
                            it.getString(5),
                            it.getString(6),
                        )
                    } shouldBe listOf(
                        listOf("anna.berg", "Anna", null, 3, "system", null),
                        listOf("anna.berg-2", "Anna", "anna@example.com", 3, "system", null),
                        listOf("fredrik.bjornoy", "Fredrik", "fredrik@example.com", 3, "system", null),
                        listOf("jean.luc.o.brien", "Jean-Luc", null, 3, "system", null),
                        listOf("kari.hansen", "Kari", null, 3, "system", null),
                        listOf("kari.hansen-2", "Kari", null, 3, "system", null),
                        listOf("kjell.olav.dale", "Kjell Olav", null, 3, "system", null),
                        listOf(
                            "ola.nordmann",
                            "Ola",
                            "Ola@Example.com",
                            3,
                            "system",
                            "ola.nordmann",
                        ),
                        listOf("orjan.johansen", "Ørjan", null, 3, "system", null),
                    )
                    c.query(
                        "select reservation_id, guest_id from reservation_guest order by reservation_id, guest_id",
                    ) {
                        it.getString(1) to it.getString(2)
                    } shouldBe listOf(
                        "r1" to "anna.berg-2",
                        "r1" to "ola.nordmann",
                        "r2" to "anna.berg",
                        "r2" to "kari.hansen",
                    )
                    c.query("select guest_id, hex(image), created_by, updated_by, version from guest_avatar") {
                        listOf(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5))
                    } shouldBe listOf(listOf("ola.nordmann", "010203", "ola.nordmann", "orjan.johansen", 2))
                    c.query("select id, created_by, updated_by from reservation order by id") {
                        Triple(it.getString(1), it.getString(2), it.getString(3))
                    } shouldBe listOf(Triple("r1", "orjan.johansen", "ola.nordmann"), Triple("r2", "system", null))
                    c.query("select count(*) from guest where created_time <> '2025-01-01T00:00:00Z'") {
                        it.getInt(1)
                    } shouldBe
                        listOf(0)
                    c.query("pragma foreign_key_check") { it.getString(1) } shouldBe emptyList()
                    c.query("select count(*) from sqlite_temp_master where name = 'guest_id_map'") {
                        it.getInt(1)
                    } shouldBe
                        listOf(0)
                }
            } finally {
                migration.delete()
            }
        }

        "should do nothing for guests that already have the new id, and for an empty database" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.7")
                migration.migrateToLatest()
                migration.withConnection { c ->
                    c.query("select count(*) from guest") { it.getInt(1) } shouldBe listOf(0)
                }
            } finally {
                migration.delete()
            }
        }

        "should give the same ids as the slug of POST /api/guests" {
            val names = listOf(
                "Ola" to "Nordmann",
                "Ørjan" to "Johansen",
                "Æsir" to "Åsen",
                "Jörg" to "Müller-Lüdenscheidt",
                "René" to "Dubois",
                "Kjell Olav" to "Dale",
                "Jean-Luc" to "O'Brien",
                "  Space  " to "  Padded  ",
                "A".repeat(20) to "b".repeat(20),
            )
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.7")
                migration.withConnection { c ->
                    names.forEachIndexed { i, (first, last) ->
                        c.execute(
                            """
                            insert into guest (created_time, id, first_name, last_name, birth_year, gender)
                            values ('2025-01-01T00:00:00Z', 'old$i', '${first.sqlEscaped()}', '${last.sqlEscaped()}', 1980, 'MALE')
                            """.trimIndent(),
                        )
                    }
                }

                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("select id from guest order by id") { it.getString(1) } shouldBe
                        names.map { (first, last) -> guestSlug(first, last) }.sorted()
                }
            } finally {
                migration.delete()
            }
        }
    })
