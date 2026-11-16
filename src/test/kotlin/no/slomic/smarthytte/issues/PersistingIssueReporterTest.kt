package no.slomic.smarthytte.issues

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CancellationException
import no.slomic.smarthytte.common.SYSTEM_ACTOR

private class FakeIssueRepository : IssueRepository {
    data class Recorded(
        val code: IssueCode,
        val key: String,
        val message: String,
        val causeType: String?,
        val actor: String,
    )

    val recorded = mutableListOf<Recorded>()
    val resolveCalls = mutableListOf<Pair<IssueCode, String>>()
    var open = true
    var failure: Exception? = null

    override suspend fun record(code: IssueCode, key: String, message: String, causeType: String?, actor: String) {
        failure?.let { throw it }
        recorded += Recorded(code, key, message, causeType, actor)
    }

    override suspend fun resolve(code: IssueCode, key: String, actor: String): Boolean {
        failure?.let { throw it }
        resolveCalls += code to key
        return open
    }

    override suspend fun findByCodeAndKey(code: IssueCode, key: String): Issue? = null
}

class PersistingIssueReporterTest :
    ShouldSpec({
        val logging = RecordingIssueReporter()
        val repository = FakeIssueRepository()
        val reporter = PersistingIssueReporter(logging = logging, repository = repository)

        beforeTest {
            logging.reports.clear()
            logging.resolved.clear()
            repository.recorded.clear()
            repository.resolveCalls.clear()
            repository.open = true
            repository.failure = null
        }

        context("report") {
            should("log first and store the code, key, message and the class name of the cause") {
                val cause = IllegalStateException("boom")

                reporter.report(IssueCode.FETCH_FAILED, key = "FETCH_FAILED", message = "failed", cause = cause)

                logging.reports.single().cause shouldBe cause
                // Only the class name is stored, the message of the cause must not reach the repository
                repository.recorded.single().toString() shouldNotContain "boom"
                repository.recorded.single() shouldBe FakeIssueRepository.Recorded(
                    code = IssueCode.FETCH_FAILED,
                    key = "FETCH_FAILED",
                    message = "failed",
                    causeType = "IllegalStateException",
                    actor = SYSTEM_ACTOR,
                )
            }

            should("store no cause type without a cause") {
                reporter.report(IssueCode.PLACE_MISSING, key = "PLACE_MISSING:1", message = "no city")

                repository.recorded.single().causeType shouldBe null
            }

            should("not throw when storing fails, and still have logged the issue") {
                repository.failure = IllegalStateException("database is locked")

                reporter.report(IssueCode.JLR_AUTH_FAILED, key = "JLR_AUTH_FAILED", message = "rejected")

                logging.reports.map { it.code } shouldContainExactly listOf(IssueCode.JLR_AUTH_FAILED)
            }

            should("pass on a cancellation") {
                repository.failure = CancellationException("stopping")

                shouldThrow<CancellationException> {
                    reporter.report(IssueCode.JLR_AUTH_FAILED, key = "JLR_AUTH_FAILED", message = "rejected")
                }
            }
        }

        context("resolve") {
            should("log and resolve in the repository") {
                reporter.resolve(IssueCode.PLACE_MISSING, key = "PLACE_MISSING:1")

                logging.resolved shouldContainExactly listOf(IssueCode.PLACE_MISSING to "PLACE_MISSING:1")
                repository.resolveCalls shouldContainExactly listOf(IssueCode.PLACE_MISSING to "PLACE_MISSING:1")
            }

            should("not fail when there is no open issue") {
                repository.open = false

                reporter.resolve(IssueCode.FETCH_FAILED, key = "FETCH_FAILED")

                repository.resolveCalls.size shouldBe 1
            }

            should("not throw when storing fails") {
                repository.failure = IllegalStateException("database is locked")

                reporter.resolve(IssueCode.FETCH_FAILED, key = "FETCH_FAILED")
            }

            should("pass on a cancellation") {
                repository.failure = CancellationException("stopping")

                shouldThrow<CancellationException> { reporter.resolve(IssueCode.FETCH_FAILED, key = "FETCH_FAILED") }
            }
        }
    })
