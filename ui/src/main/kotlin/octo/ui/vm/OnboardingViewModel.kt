package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentId
import octo.core.agent.AuthState
import octo.core.agent.PermissionMode
import octo.core.config.ConfigEdit
import octo.core.config.ConfigFile
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.orchestrator.OctoCore
import java.io.File

enum class OnboardingStep { Welcome, Repo, Agent, Done }

data class OnboardingUiState(
    /** Shown on the very first start: no `config.json` yet and no tasks. */
    val visible: Boolean = false,
    val step: OnboardingStep = OnboardingStep.Welcome,
    val repo: String = "",
    val setup: String = "",
    val checking: Boolean = false,
    /** Null until the agents have been probed. */
    val agents: List<AgentInfo>? = null,
    val agent: AgentId? = null,
    /** The chosen agent's vendor has terms the user has not accepted yet. */
    val termsPending: Boolean = false,
    val mode: PermissionMode = PermissionMode.Supervised,
    val error: String? = null,
) {
    val chosen: AgentInfo? get() = agents?.firstOrNull { it.id == agent }
}

/** What onboarding hands to the new-task dialog when it finishes. */
data class NewTaskDraft(
    val repo: String,
    val agent: AgentId?,
    val mode: PermissionMode,
)

/**
 * The first-run guide. Finishing or skipping it writes `config.json`, so it does not come back;
 * the choices go to `config.json` and to the first task's dialog.
 */
class OnboardingViewModel(
    private val file: ConfigFile,
    private val catalog: AgentCatalog,
    private val core: OctoCore,
    parent: CoroutineScope,
    private val home: String = System.getProperty("user.home"),
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutable.asStateFlow()

    init {
        act {
            val firstStart =
                file.load().text == null &&
                    core.state.value.tasks
                        .isEmpty()
            mutable.update { it.copy(visible = firstStart) }
        }
    }

    fun start() = mutable.update { it.copy(step = OnboardingStep.Repo, error = null) }

    fun back() =
        mutable.update {
            it.copy(step = OnboardingStep.entries[(it.step.ordinal - 1).coerceAtLeast(0)], error = null)
        }

    fun editRepo(value: String) = mutable.update { it.copy(repo = value, error = null) }

    fun editSetup(value: String) = mutable.update { it.copy(setup = value) }

    /** Checks that the repository is a git checkout before going on; the agents are probed meanwhile. */
    fun confirmRepo() {
        val repo = expand(state.value.repo.trim())
        if (repo.isEmpty()) return skipRepo()
        mutable.update { it.copy(checking = true, error = null) }
        act({ e -> mutable.update { it.copy(checking = false, error = "Not a git repository: ${e.userMessage}") } }) {
            core.branches(repo, null)
            mutable.update { it.copy(repo = repo, checking = false, step = OnboardingStep.Agent) }
            probe()
        }
    }

    fun skipRepo() {
        mutable.update { it.copy(repo = "", setup = "", step = OnboardingStep.Agent, error = null) }
        act { probe() }
    }

    fun selectAgent(id: AgentId) {
        if (state.value.agents
                ?.firstOrNull { it.id == id }
                ?.probe
                ?.installed != true
        ) {
            return
        }
        mutable.update { it.copy(agent = id, termsPending = false) }
        act { checkTerms(id) }
    }

    fun confirmAgent() = mutable.update { it.copy(step = OnboardingStep.Done, error = null) }

    fun selectMode(mode: PermissionMode) = mutable.update { it.copy(mode = mode) }

    /** Saves the choices and hides the guide; [onDone] gets the first task to open, if any. */
    fun finish(onDone: (NewTaskDraft?) -> Unit) {
        val s = state.value
        act {
            save(s)
            mutable.update { it.copy(visible = false) }
            onDone(if (s.repo.isEmpty()) null else NewTaskDraft(s.repo, s.agent, s.mode))
        }
    }

    /** Leaves without choosing anything; an empty `config.json` keeps the guide from coming back. */
    fun dismiss() =
        act {
            file.update { it }
            mutable.update { it.copy(visible = false) }
        }

    fun close() = scope.cancel()

    private suspend fun save(s: OnboardingUiState) {
        file.update(ConfigEdit.defaultPermissionMode(s.mode))
        if (s.repo.isNotEmpty() && s.setup.isNotBlank()) file.update(ConfigEdit.worktreeSetup(s.repo, s.setup.trim()))
    }

    private suspend fun probe() {
        val agents = catalog.agents()
        val installed = agents.filter { it.probe.installed }
        val pick = installed.firstOrNull { it.probe.auth == AuthState.LoggedIn } ?: installed.firstOrNull()
        mutable.update { it.copy(agents = agents, agent = it.agent ?: pick?.id) }
        pick?.let { checkTerms(state.value.agent ?: it.id) }
    }

    private suspend fun checkTerms(id: AgentId) {
        val pending = catalog.pendingTerms(id) != null
        mutable.update { if (it.agent == id) it.copy(termsPending = pending) else it }
    }

    private fun expand(path: String) =
        when {
            path == "~" -> home
            path.startsWith("~/") -> File(home, path.removePrefix("~/")).path
            else -> path
        }

    private fun act(
        onError: (Exception) -> Unit = { e -> mutable.update { it.copy(error = e.userMessage) } },
        block: suspend () -> Unit,
    ) = scope.action(onError, block)
}
