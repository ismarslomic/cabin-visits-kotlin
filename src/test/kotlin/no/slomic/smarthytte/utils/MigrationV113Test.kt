package no.slomic.smarthytte.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.SQLException

/** V1.13 adds the `issue` table and leaves every existing row as it is. */
class MigrationV113Test :
    StringSpec({
        @Suppress("SqlSourceToSinkFlow")
        fun Connection.execute(sql: String) = prepareStatement(sql).use { it.execute() }

        @Suppress("SqlSourceToSinkFlow")
        fun <T> Connection.query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
            prepareStatement(sql).use { st ->
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
            }

        fun Connection.insertIssue(code: String = "PLACE_MISSING", key: String = "k", status: String = "OPEN") =
            execute(
                """
            insert into issue (code, issue_key, status, message, first_occurred_time, last_occurred_time, created_time, created_by)
            values ('$code', '$key', '$status', 'm', '2026-11-16T10:00:00Z', '2026-11-16T10:00:00Z', '2026-11-16T10:00:00Z', 'system')
                """.trimIndent(),
            )

        "should create an empty issue table with exactly the expected columns" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateTo("1.12")
                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.query("select count(*) from issue") { it.getInt(1) } shouldBe listOf(0)
                    c.query("pragma table_info(issue)") { it.getString("name") } shouldContainExactlyInAnyOrder
                        listOf(
                            "id", "code", "issue_key", "status", "message", "cause_type", "occurrence_count",
                            "first_occurred_time", "last_occurred_time", "resolved_time", "read_time",
                            "created_time", "created_by", "updated_time", "updated_by", "version",
                        )
                }
            } finally {
                migration.delete()
            }
        }

        "should default to an open issue with count 1 and version 1" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.insertIssue()
                    c.query("select status, occurrence_count, version from issue") {
                        Triple(it.getString(1), it.getInt(2), it.getInt(3))
                    } shouldBe listOf(Triple("OPEN", 1, 1))
                }
            } finally {
                migration.delete()
            }
        }

        "should reject a second issue with the same code and key, and an unknown status" {
            val migration = MigrationTestSupport()
            try {
                migration.migrateToLatest()

                migration.withConnection { c ->
                    c.insertIssue()
                    shouldThrow<SQLException> { c.insertIssue() }
                    shouldThrow<SQLException> { c.insertIssue(key = "other", status = "CLOSED") }
                    c.insertIssue(code = "FETCH_FAILED")
                }
            } finally {
                migration.delete()
            }
        }
    })
