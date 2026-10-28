package octo.core.orchestrator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import octo.core.agent.AgentEvent
import octo.core.agent.ToolStatus
import octo.core.git.Workspace
import octo.core.process.ProcessResult
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.Transcript
import octo.core.task.TranscriptEntry
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a repository's setup command (installing dependencies, copying `.env` files) in a task's
 * new worktree and shows it in the transcript as a tool call. [commands] are keyed by repository
 * root without a trailing slash.
 */
internal class WorktreeSetup(
    private val commands: Map<String, String>,
    private val transcripts: MutableStateFlow<Map<TaskId, List<TranscriptEntry>>>,
    private val workspace: (Task) -> Workspace,
) {
    // Null when the worktree is ready, else why not, so the agent never starts on a half-prepared
    // worktree; whatever the command or the machine throws counts as that failure.
    @Suppress("TooGenericExceptionCaught")
    suspend fun run(task: Task): String? {
        val command = commands[task.repo.trimEnd('/')] ?: return null
        val title = "Setup: $command"
        entry(task.id, title, ToolStatus.InProgress)
        val result =
            try {
                withTimeoutOrNull(TIMEOUT_MS) { workspace(task).runSetup(task.worktree, command) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProcessResult(-1, "", e.message ?: "The setup command did not run")
            }
        val output = result?.let { listOf(it.stdout, it.stderr).filter(String::isNotBlank).joinToString("\n") }
        val detail = output?.takeLast(MAX_OUTPUT)?.ifBlank { null }
        val ok = result?.exitCode == 0
        entry(task.id, title, if (ok) ToolStatus.Completed else ToolStatus.Failed, detail)
        val why = result?.let { "exited with code ${it.exitCode}" } ?: "timed out"
        return if (ok) null else "The setup command $why; the agent was not started"
    }

    private fun entry(
        id: TaskId,
        title: String,
        status: ToolStatus,
        detail: String? = null,
    ) {
        val event = AgentEvent.ToolCall(TOOL_ID, title, "execute", status, detail)
        transcripts.update { it + (id to Transcript.reduce(it[id].orEmpty(), event)) }
    }

    private companion object {
        const val TIMEOUT_MS = 30 * 60_000L
        const val MAX_OUTPUT = 4_000
        const val TOOL_ID = "octo.setup"
    }
}
