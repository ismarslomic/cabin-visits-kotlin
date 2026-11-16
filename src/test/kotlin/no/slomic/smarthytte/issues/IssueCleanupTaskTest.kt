package no.slomic.smarthytte.issues

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Counts the cleanups, one per `resolveOpenBefore`, and throws on the first one. The rest is not used here. */
private class CountingIssueRepository : IssueRepository {
    val cleanups = Channel<Int>(Channel.UNLIMITED)
    private val count = AtomicInteger()

    val total: Int get() = count.get()

    override suspend fun resolveOpenBefore(code: IssueCode, occurredBefore: Instant, actor: String): Int {
        val number = count.incrementAndGet()
        cleanups.send(number)
        check(number > 1) { "the first cleanup fails" }
        return 0
    }

    override suspend fun deleteResolvedBefore(cutoff: Instant): Int = 0

    override suspend fun record(code: IssueCode, key: String, message: String, causeType: String?, actor: String) =
        error("not used")

    override suspend fun resolve(code: IssueCode, key: String, actor: String): Boolean = error("not used")

    override suspend fun findByCodeAndKey(code: IssueCode, key: String): Issue? = error("not used")

    override suspend fun findById(id: Long): Issue? = error("not used")

    override suspend fun list(isRead: Boolean?, status: IssueStatus?, limit: Int): List<Issue> = error("not used")

    override suspend fun unreadCount(): Int = error("not used")

    override suspend fun setRead(id: Long, expectedVersion: Short, read: Boolean, actor: String): IssueReadOutcome =
        error("not used")
}

class IssueCleanupTaskTest :
    StringSpec({
        "the task cleans up at once, goes on after a failing run and stops with the application" {
            val repository = CountingIssueRepository()

            testApplication {
                application { startIssueCleanup(repository, 15.minutes, interval = 10.milliseconds) }
                startApplication()

                // run 1 throws and the loop survives it, run 2 and 3 show that it goes on
                withTimeout(5.seconds) {
                    repository.cleanups.receive() shouldBe 1
                    repository.cleanups.receive() shouldBe 2
                    repository.cleanups.receive() shouldBe 3
                }
            }

            delay(100.milliseconds)
            val afterStop = repository.total
            delay(100.milliseconds)
            repository.total shouldBe afterStop
        }
    })
