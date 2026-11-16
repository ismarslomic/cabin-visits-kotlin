package no.slomic.smarthytte.issues

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.logging.KtorSimpleLogger
import org.slf4j.LoggerFactory

private const val LOGGER_NAME = "test.LoggingIssueReporter"

class LoggingIssueReporterTest :
    ShouldSpec({
        suspend fun withCapturedLog(block: suspend (LoggingIssueReporter) -> Unit): List<ILoggingEvent> {
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            val logbackLogger = LoggerFactory.getLogger(LOGGER_NAME) as Logger
            logbackLogger.level = Level.DEBUG
            logbackLogger.addAppender(appender)
            try {
                block(LoggingIssueReporter(KtorSimpleLogger(LOGGER_NAME)))
            } finally {
                logbackLogger.detachAppender(appender)
            }
            return appender.list
        }

        context("level per code") {
            IssueCode.entries.forEach { code ->
                should("log $code at the severity of the code") {
                    val events = withCapturedLog { it.report(code, key = "k", message = "m") }

                    val expected = if (code.severity == IssueSeverity.WARN) Level.WARN else Level.ERROR
                    events.single().level shouldBe expected
                }
            }
        }

        context("format") {
            should("write code, key and message") {
                val events = withCapturedLog {
                    it.report(IssueCode.PLACE_MISSING, key = "PLACE_MISSING:42", message = "no city")
                }

                events.single().formattedMessage shouldBe "[PLACE_MISSING] key=PLACE_MISSING:42 no city"
            }

            should("attach the cause") {
                val cause = IllegalStateException("boom")

                val events = withCapturedLog {
                    it.report(IssueCode.FETCH_FAILED, key = "FETCH_FAILED", message = "failed", cause = cause)
                }

                events.single().throwableProxy.message shouldBe "boom"
            }

            should("log without a cause") {
                val events = withCapturedLog {
                    it.report(IssueCode.JLR_APPROVAL_REQUIRED, key = "JLR_APPROVAL_REQUIRED", message = "approve")
                }

                events.single().throwableProxy shouldBe null
            }
        }

        context("resolve") {
            should("log at debug, because a healthy run resolves codes that never failed") {
                val events = withCapturedLog { it.resolve(IssueCode.JLR_AUTH_FAILED, key = "JLR_AUTH_FAILED") }

                events.single().level shouldBe Level.DEBUG
                events.single().formattedMessage shouldBe "[JLR_AUTH_FAILED] key=JLR_AUTH_FAILED resolved"
            }
        }
    })
