package no.slomic.smarthytte.vehicletrips.jlr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.slomic.smarthytte.utils.TestDbSetup
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SqliteJlrTokenRepositoryTest :
    StringSpec({
        val testDbSetup = TestDbSetup()

        beforeTest { testDbSetup.setupDb() }

        afterTest { testDbSetup.teardownDb() }

        val repository: JlrTokenRepository = SqliteJlrTokenRepository()

        "find returns null when no token is stored" {
            repository.findRefreshToken().shouldBeNull()
        }

        "save then find returns the token" {
            repository.saveRefreshToken("refresh-1")

            repository.findRefreshToken() shouldBe "refresh-1"
        }

        "save replaces the token, there is only ever one row" {
            repository.saveRefreshToken("refresh-1")
            repository.saveRefreshToken("refresh-2")

            repository.findRefreshToken() shouldBe "refresh-2"
            transaction { JlrTokenTable.selectAll().count() } shouldBe 1
        }

        "delete removes the token" {
            repository.saveRefreshToken("refresh-1")

            repository.deleteRefreshToken()

            repository.findRefreshToken().shouldBeNull()
        }
    })
