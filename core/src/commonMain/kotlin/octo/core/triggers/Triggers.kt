package octo.core.triggers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import octo.core.access.Secrets
import octo.core.http.bearerToken
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile
import octo.core.io.ReadOnlyFileException
import octo.core.orchestrator.OctoCore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How a webhook delivery ended, for the HTTP answer. */
sealed interface Delivery {
    data object Accepted : Delivery

    data class Skipped(
        val reason: String,
    ) : Delivery

    data object Unauthorized : Delivery

    data object NotFound : Delivery
}

/** Webhook deliveries as the remote API hands them over; [headers] have lowercase names. */
fun interface Webhooks {
    suspend fun deliver(
        id: String,
        headers: Map<String, String>,
        body: String,
    ): Delivery
}

/** What `triggers.json` keeps of one trigger. */
@Serializable
data class TriggerRecord(
    val id: String,
    /** Epoch milliseconds of the last scheduled run. */
    val lastRun: Long? = null,
    /** The latest delivery ids, so a repeated delivery starts nothing. */
    val deliveries: List<String> = emptyList(),
)

/**
 * Starts tasks from `config.json` templates on a schedule or when a webhook is called. Every
 * failure becomes a warning; a broken trigger never stops the others or Octo.
 */
class Triggers private constructor(
    private val schedules: List<Pair<TriggerConfig, Cron>>,
    private val hooks: Map<String, Pair<TriggerConfig, WebhookConfig>>,
    private val core: OctoCore,
    private val file: JsonListFile<TriggerRecord>,
    private val secrets: Secrets,
    private val clock: Clock,
    private val scope: CoroutineScope,
    /** Problems with the config or `triggers.json`; the triggers concerned are skipped. */
    val warnings: List<String>,
) : Webhooks {
    // Kept here as well as on disk, so a read-only file still suppresses duplicates until Octo exits.
    private val lock = Mutex()
    private val records =
        file.items.value
            .associateBy { it.id }
            .toMutableMap()

    private fun start() = schedules.forEach { (trigger, cron) -> scope.launch { schedule(trigger, cron) } }

    /** Stops the schedules and any task start still on its way. */
    fun close() = scope.cancel()

    private suspend fun schedule(
        trigger: TriggerConfig,
        cron: Cron,
    ) {
        // A new trigger counts from now, so it never fires for the past.
        var last =
            lock.withLock { records[trigger.id]?.lastRun }?.let(Instant::fromEpochMilliseconds)
                ?: clock.now().also { now -> update(trigger.id) { it.copy(lastRun = now.toEpochMilliseconds()) } }
        while (true) {
            val due = next(trigger, cron, last) ?: return
            // Short naps against the wall clock: a computer that slept wakes up to its due runs.
            while (clock.now() < due) delay(minOf(due - clock.now(), NAP))
            // Every run missed while Octo was closed or asleep collapses into this one.
            last = clock.now()
            update(trigger.id) { it.copy(lastRun = last.toEpochMilliseconds()) }
            start(trigger)?.let { core.warn(it) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun next(
        trigger: TriggerConfig,
        cron: Cron,
        after: Instant,
    ): Instant? =
        try {
            cron.next(after)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            core.warn("Trigger '${trigger.id}' stopped: ${e.message}")
            null
        }

    /** Null when the task started, otherwise the warning to show. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun start(trigger: TriggerConfig): String? =
        try {
            core.createTask(trigger.task.newTask())
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Trigger '${trigger.id}' could not start its task: ${e.message}"
        }

    override suspend fun deliver(
        id: String,
        headers: Map<String, String>,
        body: String,
    ): Delivery {
        val hook = hooks[id]
        return when {
            hook == null -> Delivery.NotFound
            !signed(hook.second.secret, headers, body) -> Delivery.Unauthorized
            else -> skipped(hook.second, headers, body)?.let(Delivery::Skipped) ?: accept(hook.first, headers, body)
        }
    }

    // Answered before the task starts: creating a worktree can outlast a sender's timeout.
    private suspend fun accept(
        trigger: TriggerConfig,
        headers: Map<String, String>,
        body: String,
    ): Delivery {
        val key = headers[DELIVERY] ?: secrets.sha256(body.encodeToByteArray()).toHexString()
        val claimed =
            lock.withLock {
                val seen = records[trigger.id]?.deliveries.orEmpty()
                (key !in seen).also { fresh ->
                    if (fresh) change(trigger.id) { it.copy(deliveries = (seen + key).takeLast(KEPT)) }
                }
            }
        if (!claimed) return Delivery.Skipped("delivery $key was already handled")
        scope.launch {
            start(trigger)?.let { warning ->
                // A redelivery after a failed start should get its chance.
                update(trigger.id) { it.copy(deliveries = it.deliveries - key) }
                core.warn(warning)
            }
        }
        return Delivery.Accepted
    }

    private fun signed(
        secret: String,
        headers: Map<String, String>,
        body: String,
    ): Boolean {
        val signature = headers[SIGNATURE]?.removePrefix("sha256=")?.lowercase()
        val expected = secrets.hmacSha256(secret.encodeToByteArray(), body.encodeToByteArray()).toHexString()
        return (signature != null && same(signature, expected)) ||
            bearerToken(headers["authorization"])?.let { same(it, secret) } == true
    }

    // Hashes first, so how long the comparison takes says nothing about the secret.
    private fun same(
        a: String,
        b: String,
    ) = secrets.sha256(a.encodeToByteArray()).contentEquals(secrets.sha256(b.encodeToByteArray()))

    private fun skipped(
        hook: WebhookConfig,
        headers: Map<String, String>,
        body: String,
    ): String? {
        val event = headers[EVENT]
        return when {
            hook.events.isNotEmpty() && event !in hook.events -> "event '$event' is not one of ${hook.events}"
            hook.filters.isEmpty() -> null
            else -> unmatched(hook.filters, body)
        }
    }

    private fun unmatched(
        filters: Map<String, String>,
        body: String,
    ): String? {
        val json = parse(body) ?: return "the body is not JSON"
        return filters.entries
            .firstOrNull { (path, wanted) -> value(json, path) != wanted }
            ?.let { "${it.key} is not '${it.value}'" }
    }

    private suspend fun update(
        id: String,
        change: (TriggerRecord) -> TriggerRecord,
    ) = lock.withLock { change(id, change) }

    // Called with the lock held.
    @Suppress("SwallowedException")
    private suspend fun change(
        id: String,
        change: (TriggerRecord) -> TriggerRecord,
    ) {
        records[id] = change(records[id] ?: TriggerRecord(id))
        val all = records.values.toList()
        try {
            file.change { all }
        } catch (e: ReadOnlyFileException) {
            // Already among the start-up warnings; the records live on in memory.
        } catch (e: IOException) {
            core.warn("Could not save triggers.json: ${e.message}")
        }
    }

    companion object {
        /** Long enough to cost nothing, short enough that a woken computer catches up soon. */
        private val NAP = 1.minutes
        private const val KEPT = 100
        private const val SIGNATURE = "x-hub-signature-256"
        private const val DELIVERY = "x-github-delivery"
        private const val EVENT = "x-github-event"

        /** Checks [configs], loads `triggers.json` from [file] and starts the schedules in [scope]. */
        @Suppress("LongParameterList")
        suspend fun open(
            configs: List<TriggerConfig>,
            core: OctoCore,
            file: AtomicFile,
            cron: CronParser,
            secrets: Secrets,
            scope: CoroutineScope,
            clock: Clock = Clock.System,
        ): Triggers {
            val warnings = mutableListOf<String>()
            val ids = mutableSetOf<String>()
            val valid =
                configs.filter { config ->
                    val problem =
                        config.problem() ?: "Trigger id '${config.id}' is used twice".takeIf { config.id in ids }
                    ids += config.id
                    problem?.let(warnings::add) == null
                }
            val schedules =
                valid.mapNotNull { config ->
                    config.schedule?.let { expression ->
                        try {
                            config to cron.parse(expression)
                        } catch (e: IllegalArgumentException) {
                            warnings += "Trigger '${config.id}': ${e.message}"
                            null
                        }
                    }
                }
            val hooks = valid.mapNotNull { config -> config.webhook?.let { config.id to (config to it) } }.toMap()
            val records = JsonListFile.open(file, TriggerRecord.serializer(), "triggers")
            records.warning?.let(warnings::add)
            // Its own child scope, so close() stops the triggers and nothing else.
            val own = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
            return Triggers(schedules, hooks, core, records, secrets, clock, own, warnings).also { it.start() }
        }

        // A body that is not JSON only fails the filters, so its error is not worth keeping.
        @Suppress("SwallowedException")
        private fun parse(body: String): JsonElement? =
            try {
                Json.parseToJsonElement(body)
            } catch (e: IllegalArgumentException) {
                null
            }

        private fun value(
            json: JsonElement,
            path: String,
        ): String? =
            path
                .split('.')
                .fold(json as JsonElement?) { element, key ->
                    when (element) {
                        is JsonObject -> element[key]
                        is JsonArray -> key.toIntOrNull()?.let(element::getOrNull)
                        else -> null
                    }
                }.let { (it as? JsonPrimitive)?.content }
    }
}
