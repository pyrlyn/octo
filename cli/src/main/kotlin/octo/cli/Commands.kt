package octo.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.enum
import octo.core.access.ApiJson
import octo.core.access.CreatedBody
import octo.core.access.NewTaskBody
import octo.core.access.PairBody
import octo.core.access.PairedBody
import octo.core.access.PromptBody
import octo.core.access.TaskBody
import octo.core.access.TaskListBody
import octo.core.access.TranscriptBody
import octo.core.agent.PermissionMode
import octo.core.status.AgentStatus
import java.io.File

class PairCommand(
    private val octo: Octo,
) : CliktCommand(name = "pair") {
    private val code by argument(help = "The code Octo's Devices dialog shows")
    private val name by option("--name", help = "How Octo lists this device").default("Command line")

    override fun help(context: Context) = "Pair with Octo and keep the token for the other commands"

    override fun run() {
        val client = octo.client(paired = false)
        val paired =
            client.decode(
                client.post("/pair", PairBody.serializer(), PairBody(code, name)),
                PairedBody.serializer(),
            )
        octo.credentials.write(Credentials(octo.address, paired.token))
        line("Paired with ${octo.address}; the token is in ${octo.credentials.path}")
    }
}

class TaskCommand : CliktCommand(name = "task") {
    override fun help(context: Context) = "List, start, message, read and wait for tasks"

    override fun run() = Unit
}

class TaskListCommand(
    private val octo: Octo,
) : CliktCommand(name = "list") {
    private val json by jsonFlag()

    override fun help(context: Context) = "List the tasks: id, status, agent and title, one per line"

    override fun run() {
        val client = octo.client()
        val body = client.get("/tasks")
        if (json) return line(body)
        client.decode(body, TaskListBody.serializer()).tasks.forEach {
            line(listOf(it.id.value, it.status, it.agent, it.title).joinToString("\t"))
        }
    }
}

class TaskStartCommand(
    private val octo: Octo,
) : CliktCommand(name = "start") {
    private val repo by option("--repo", help = "The repository; defaults to the current directory")
    private val base by option("--base", help = "The branch the worktree starts from").required()
    private val agent by option("--agent", help = "The agent's id, e.g. claude").required()
    private val title by option("--title").required()
    private val prompt by option("--prompt", help = "The first message")
    private val mode by option("--mode", help = "The permission mode").enum<PermissionMode>(ignoreCase = true)
    private val machine by option("--machine", help = "A machine from Octo's config instead of this one")
    private val options by option("--option", help = "An agent option, as key=value").associate()
    private val json by jsonFlag()

    override fun help(context: Context) = "Start a task and print its id"

    override fun run() {
        val client = octo.client()
        val body =
            NewTaskBody(
                // Octo may run in another directory, so a relative path means the caller's.
                repo = File(octo.cwd).resolve(repo ?: ".").normalize().path,
                baseRef = base,
                agent = agent,
                title = title,
                prompt = prompt,
                options = options,
                machine = machine,
                permissionMode = mode,
            )
        val created = client.post("/tasks", NewTaskBody.serializer(), body)
        line(if (json) created else client.decode(created, CreatedBody.serializer()).id.value)
    }
}

class TaskSendCommand(
    private val octo: Octo,
) : CliktCommand(name = "send") {
    private val id by argument(help = "The task's id")
    private val text by argument(help = "The message; several words are joined with spaces").multiple(required = true)

    override fun help(context: Context) = "Send a message; Octo queues it while the agent works"

    override fun run() {
        octo.client().post("/tasks/$id/prompt", PromptBody.serializer(), PromptBody(text.joinToString(" ")))
    }
}

class TaskReadCommand(
    private val octo: Octo,
) : CliktCommand(name = "read") {
    private val id by argument(help = "The task's id")
    private val json by jsonFlag()

    override fun help(context: Context) = "Print the task's transcript"

    override fun run() {
        val client = octo.client()
        val body = client.get("/tasks/$id/transcript")
        if (json) return line(body)
        client.decode(body, TranscriptBody.serializer()).entries.forEach { line(TranscriptText.line(it)) }
    }
}

class TaskWaitCommand(
    private val octo: Octo,
) : CliktCommand(name = "wait") {
    private val id by argument(help = "The task's id")
    private val timeout by option("--timeout", help = "Give up after this many seconds").double()
    private val json by jsonFlag()

    override fun help(context: Context) =
        "Wait until the task's turn ends. Exits 0 when idle, 1 when failed or stopped, 2 when it waits for an " +
            "answer, $TIMED_OUT on timeout"

    override fun run() {
        val client = octo.client()
        val deadline = timeout?.let { octo.now() + (it * MILLIS).toLong() }
        var task = find(client)
        while (task.status in BUSY && (deadline == null || octo.now() < deadline)) {
            octo.sleep(POLL_MS)
            task = find(client)
        }
        line(
            if (json) {
                ApiJson.encodeToString(TaskBody.serializer(), task)
            } else {
                listOfNotNull(task.id.value, task.status.name, task.failure).joinToString("\t")
            },
        )
        val code =
            when (task.status) {
                in BUSY -> TIMED_OUT
                AgentStatus.Idle -> 0
                AgentStatus.Blocked -> BLOCKED
                else -> 1
            }
        if (code != 0) throw ProgramResult(code)
    }

    private fun find(client: OctoClient) =
        client.get("/tasks", TaskListBody.serializer()).tasks.firstOrNull { it.id.value == id }
            ?: throw CliktError("Octo has no task $id")

    private companion object {
        val BUSY = setOf(AgentStatus.Starting, AgentStatus.Working)
        const val POLL_MS = 1_000L
        const val MILLIS = 1_000
        const val BLOCKED = 2

        /** As `timeout(1)` exits, so scripts tell a slow turn from a failed one. */
        const val TIMED_OUT = 124
    }
}

private fun CliktCommand.jsonFlag() = option("--json", help = "Print Octo's JSON instead of text").flag()

// Mordant, behind echo, turns tabs into spaces; scripts split these lines on tabs.
private fun CliktCommand.line(text: String) = currentContext.terminal.rawPrint("$text\n")
