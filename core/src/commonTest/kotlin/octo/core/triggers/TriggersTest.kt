package octo.core.triggers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.access.FakeSecrets
import octo.core.io.AtomicFile
import octo.core.orchestrator.Setup
import octo.core.orchestrator.setup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TriggersTest {
    private val task = TaskTemplate(repo = "/src/app", baseRef = "main", agent = "fake", title = "Nightly")
    private val nightly = TriggerConfig("nightly", schedule = "hourly", task = task)
    private val secret = "a-secret-of-some-length"
    private val hook = TriggerConfig("pr", webhook = WebhookConfig(secret), task = task.copy(title = "Review"))

    /** Every whole hour; any other expression is refused, as a real parser would. */
    private val hourly =
        CronParser { expression ->
            require(expression == "hourly") { "'$expression' is not a schedule" }
            Cron { after -> Instant.fromEpochMilliseconds((after.toEpochMilliseconds() / HOUR + 1) * HOUR) }
        }

    private class Run(
        val s: Setup,
        val triggers: Triggers,
        val file: AtomicFile,
    ) {
        val titles get() =
            s.core.state.value.tasks
                .map { it.task.title }

        // A task is created off the test scheduler, on real file I/O.
        suspend fun titles(count: Int): List<String> {
            s.core.state.first { it.tasks.size >= count }
            return titles
        }

        suspend fun warned(text: String) = s.core.state.first { state -> state.warnings.any { text in it } }
    }

    /** [start] moves the clock ahead of the test scheduler, as if Octo had been closed for a while. */
    private suspend fun TestScope.run(
        vararg configs: TriggerConfig,
        start: Duration = Duration.ZERO,
        file: suspend (Path) -> AtomicFile = { AtomicFile(Path(it, "triggers.json")) },
    ): Run {
        var next = 0
        val clock =
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime) + start
            }
        val s = setup(newId = { "id${next++}" }, clock = clock)
        val records = file(s.home)
        val triggers = Triggers.open(configs.toList(), s.core, records, hourly, FakeSecrets(), backgroundScope, clock)
        runCurrent()
        return Run(s, triggers, records)
    }

    @Test
    fun aScheduleStartsItsTaskWhenItIsDueAndAgainAfter() =
        runTest {
            val r = run(nightly)
            advanceTimeBy(59.minutes)
            assertEquals(emptyList(), r.titles)
            advanceTimeBy(2.minutes)
            assertEquals(listOf("Nightly"), r.titles(1))
            advanceTimeBy(1.hours)
            assertEquals(listOf("Nightly", "Nightly"), r.titles(2))
            assertTrue(r.file.read()!!.contains("\"lastRun\": ${2 * HOUR}"), "the last run survives a restart")
        }

    @Test
    fun aNewTriggerNeverRunsForThePast() =
        runTest {
            val r = run(nightly, start = 10.hours)
            assertEquals(emptyList(), r.titles)
            advanceTimeBy(1.hours + 1.minutes)
            assertEquals(listOf("Nightly"), r.titles(1))
        }

    @Test
    fun runsMissedWhileOctoWasClosedCollapseIntoOne() =
        runTest {
            val r =
                run(nightly, start = 10.hours) { home ->
                    AtomicFile(Path(home, "triggers.json")).also {
                        it.write("""{"version": 1, "triggers": [{"id": "nightly", "lastRun": 0}]}""")
                    }
                }
            assertEquals(listOf("Nightly"), r.titles(1))
            advanceTimeBy(59.minutes)
            assertEquals(listOf("Nightly"), r.titles(1))
        }

    @Test
    fun brokenTriggersAreWarnedAboutAndSkipped() =
        runTest {
            val r =
                run(
                    nightly.copy(id = "bad id"),
                    nightly.copy(id = "both", webhook = WebhookConfig(secret)),
                    TriggerConfig("short", webhook = WebhookConfig("short"), task = task),
                    nightly.copy(id = "blank", task = task.copy(repo = " ")),
                    nightly.copy(id = "typo", schedule = "every hour"),
                    nightly,
                    nightly.copy(task = task.copy(title = "Twice")),
                )
            assertEquals(6, r.triggers.warnings.size, r.triggers.warnings.toString())
            assertTrue(r.triggers.warnings.any { "'every hour'" in it })
            advanceTimeBy(1.hours + 1.minutes)
            assertEquals(listOf("Nightly"), r.titles(1), "only the first of two triggers with one id runs")
        }

    @Test
    fun aTaskThatCannotStartBecomesAWarning() =
        runTest {
            val r = run(nightly.copy(task = task.copy(agent = "missing")))
            advanceTimeBy(1.hours + 1.minutes)
            r.warned("Trigger 'nightly' could not start its task")
            assertEquals(emptyList(), r.titles)
        }

    @Test
    fun aWebhookNeedsItsSignatureOrSecret() =
        runTest {
            val r = run(hook)
            val body = """{"action":"opened"}"""
            val signature = FakeSecrets().hmacSha256(secret.encodeToByteArray(), body.encodeToByteArray()).toHexString()
            val signed = mapOf("x-hub-signature-256" to "sha256=$signature", "x-github-delivery" to "d1")
            assertEquals(Delivery.NotFound, r.triggers.deliver("other", signed, body))
            assertEquals(Delivery.NotFound, r.triggers.deliver("nightly", signed, body), "a schedule is no webhook")
            assertEquals(Delivery.Unauthorized, r.triggers.deliver("pr", emptyMap(), body))
            assertEquals(Delivery.Unauthorized, r.triggers.deliver("pr", signed, """{"action":"closed"}"""))
            assertEquals(
                Delivery.Unauthorized,
                r.triggers.deliver("pr", mapOf("authorization" to "Bearer guess"), body),
            )
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", signed, body))
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", mapOf("authorization" to "Bearer $secret"), body))
            runCurrent()
            assertEquals(listOf("Review", "Review"), r.titles(2))
        }

    @Test
    fun aRepeatedDeliveryStartsNothing() =
        runTest {
            val r = run(hook)
            val bearer = mapOf("authorization" to "Bearer $secret")
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", bearer + ("x-github-delivery" to "d1"), "{}"))
            assertTrue(r.triggers.deliver("pr", bearer + ("x-github-delivery" to "d1"), "{}") is Delivery.Skipped)
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", bearer, """{"n":1}"""))
            assertTrue(
                r.triggers.deliver("pr", bearer, """{"n":1}""") is Delivery.Skipped,
                "the body stands in for an id",
            )
            runCurrent()
            assertEquals(2, r.titles(2).size)
        }

    @Test
    fun aDeliveryWhoseTaskFailedMayComeAgain() =
        runTest {
            val r = run(hook.copy(task = task.copy(agent = "missing")))
            val headers = mapOf("authorization" to "Bearer $secret", "x-github-delivery" to "d1")
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", headers, "{}"))
            r.warned("could not start")
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", headers, "{}"))
        }

    @Test
    fun eventsAndFiltersPickTheDeliveriesThatCount() =
        runTest {
            val filtered =
                hook.copy(
                    webhook =
                        WebhookConfig(
                            secret,
                            events = listOf("pull_request"),
                            filters =
                                mapOf("pull_request.base.ref" to "main"),
                        ),
                )
            val r = run(filtered)
            val bearer = mapOf("authorization" to "Bearer $secret")
            val pr = bearer + ("x-github-event" to "pull_request")
            val main = """{"pull_request":{"base":{"ref":"main"}}}"""
            assertTrue(r.triggers.deliver("pr", bearer + ("x-github-event" to "push"), main) is Delivery.Skipped)
            assertTrue(r.triggers.deliver("pr", pr, """{"pull_request":{"base":{"ref":"dev"}}}""") is Delivery.Skipped)
            assertTrue(r.triggers.deliver("pr", pr, "not json") is Delivery.Skipped)
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", pr, main))
            runCurrent()
            assertEquals(listOf("Review"), r.titles(1))
        }

    @Test
    fun aBrokenTriggersFileKeepsTriggersWorking() =
        runTest {
            val r =
                run(hook, nightly) { home ->
                    AtomicFile(Path(home, "triggers.json")).also { it.write("{broken") }
                }
            assertTrue(r.triggers.warnings.any { "read-only" in it }, r.triggers.warnings.toString())
            val headers = mapOf("authorization" to "Bearer $secret", "x-github-delivery" to "d1")
            assertEquals(Delivery.Accepted, r.triggers.deliver("pr", headers, "{}"))
            assertTrue(
                r.triggers.deliver("pr", headers, "{}") is Delivery.Skipped,
                "duplicates are still caught in memory",
            )
            advanceTimeBy(1.hours + 1.minutes)
            assertEquals(listOf("Review", "Nightly"), r.titles(2))
            assertEquals("{broken", r.file.read())
        }

    @Test
    fun closingStopsTheSchedules() =
        runTest {
            val r = run(nightly)
            r.triggers.close()
            advanceTimeBy(2.hours)
            assertEquals(emptyList(), r.titles)
        }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
