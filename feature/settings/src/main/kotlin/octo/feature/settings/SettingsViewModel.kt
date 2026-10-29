package octo.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import octo.core.agent.PermissionMode
import octo.core.config.AppearanceConfig
import octo.core.config.ConfigEdit
import octo.core.config.ConfigFile
import octo.core.config.ConfigSnapshot
import octo.core.config.MachineConfig
import octo.core.config.OctoConfig
import octo.core.config.RemoteAccessConfig
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlag
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.feature.common.action
import octo.feature.common.childScope
import octo.feature.common.userMessage
import java.awt.Desktop
import java.io.File

enum class SettingsSection(
    val title: String,
) {
    General("General"),
    Agents("Agents"),
    Permissions("Permissions"),
    Devices("Devices"),
    Appearance("Appearance"),
    Advanced("Advanced"),
}

data class SettingsUiState(
    val section: SettingsSection = SettingsSection.General,
    /** Null until `config.json` has been read. */
    val snapshot: ConfigSnapshot? = null,
    /** Null until the agents have been probed, which happens the first time their section opens. */
    val agents: List<AgentInfo>? = null,
    /** Something Octo reads only at start changed since it started. */
    val restartNeeded: Boolean = false,
    val error: String? = null,
) {
    val config: OctoConfig get() = snapshot?.config ?: OctoConfig()

    /** Edits are refused while the file cannot be read, so it is never overwritten. */
    val editable: Boolean get() = snapshot != null && snapshot.warning == null
}

/**
 * The settings screen: every change is written to `config.json` straight away. Appearance applies
 * at once; the rest is read by core only at start, so the screen asks for a restart.
 */
class SettingsViewModel(
    private val file: ConfigFile,
    private val catalog: AgentCatalog,
    /** Every flag this build knows, the adapters' included. */
    private val flags: List<FeatureFlag>,
    /** The Octo home folder. */
    val home: String,
    parent: CoroutineScope,
    private val open: (String) -> Unit = ::openWithSystem,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutable.asStateFlow()

    val path: String get() = file.path

    /** What core is running with: the file as it was when the window opened. */
    private var started: OctoConfig? = null

    init {
        act { show(file.load()) }
    }

    fun flags(section: SettingsSection): List<FeatureFlag> =
        when (section) {
            SettingsSection.General -> {
                flags.filter {
                    it in CoreFlags.all && it !in DEVICE_FLAGS &&
                        it !in ADVANCED_FLAGS
                }
            }

            SettingsSection.Agents -> {
                flags.filter { it !in CoreFlags.all }
            }

            SettingsSection.Devices -> {
                DEVICE_FLAGS
            }

            SettingsSection.Advanced -> {
                ADVANCED_FLAGS
            }

            else -> {
                emptyList()
            }
        }

    fun select(section: SettingsSection) {
        mutable.update { it.copy(section = section) }
        if (section == SettingsSection.Agents && state.value.agents == null) act { probe() }
    }

    fun refreshAgents() = act { probe() }

    /** Reads `config.json` again, for changes made outside this screen. */
    fun reload() = act { show(file.load()) }

    fun setFlag(
        flag: FeatureFlag,
        on: Boolean,
    ) = edit(ConfigEdit.flag(flag.id, on))

    fun setPermissionMode(mode: PermissionMode) = edit(ConfigEdit.defaultPermissionMode(mode))

    /** Why [repo] and [command] cannot be saved, or null once the save is under way. */
    fun saveWorktreeSetup(
        repo: String,
        command: String,
        previousRepo: String? = null,
    ): String? {
        val path = repo.trim()
        if (!File(path).isAbsolute) return "Use the repository's absolute path"
        act {
            if (previousRepo != null && previousRepo != path) file.update(ConfigEdit.worktreeSetup(previousRepo, null))
            show(file.update(ConfigEdit.worktreeSetup(path, command.trim().ifEmpty { null })))
        }
        return null
    }

    fun removeWorktreeSetup(repo: String) = edit(ConfigEdit.worktreeSetup(repo, null))

    /** Why [text] is not a port Octo can use, in Octo's own words, or null once it is saved. */
    fun savePort(text: String): String? {
        val port = text.trim().toIntOrNull()
        val problem = if (port == null) "remoteAccess.port must be a number" else RemoteAccessConfig(port).problem()
        if (problem == null && port != null) edit(ConfigEdit.remotePort(port))
        return problem
    }

    /** Why [machine] cannot be saved, or null once the save is under way. */
    fun saveMachine(
        previousId: String?,
        machine: MachineConfig,
    ): String? {
        val taken =
            state.value.config.machines
                .any { it.id == machine.id && it.id != previousId }
        val problem = machine.problem() ?: if (taken) "There is already a machine called '${machine.id}'" else null
        if (problem == null) edit(ConfigEdit.machine(previousId, machine))
        return problem
    }

    fun removeMachine(id: String) = edit(ConfigEdit.machine(id, null))

    fun setAppearance(appearance: AppearanceConfig) = edit(ConfigEdit.appearance(appearance))

    fun reset() = act { show(file.reset()) }

    fun openConfig() =
        act {
            // The OS cannot open a file that is not there yet.
            if (state.value.snapshot?.text == null) show(file.update { it })
            withContext(Dispatchers.IO) { open(path) }
        }

    fun revealHome() = act { withContext(Dispatchers.IO) { open(home) } }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun edit(edit: ConfigEdit) = act { show(file.update(edit)) }

    private suspend fun probe() {
        val agents = catalog.agents()
        mutable.update { it.copy(agents = agents) }
    }

    private fun show(snapshot: ConfigSnapshot) {
        val first = started ?: snapshot.config.also { started = it }
        val applied = snapshot.config.copy(appearance = first.appearance)
        mutable.update { it.copy(snapshot = snapshot, restartNeeded = applied != first, error = null) }
    }

    private fun act(block: suspend () -> Unit) =
        scope.action({ e ->
            mutable.update {
                it.copy(error = e.userMessage)
            }
        }, block)

    private companion object {
        val DEVICE_FLAGS =
            listOf(CoreFlags.REMOTE_ACCESS, CoreFlags.GUEST_SHARE, CoreFlags.REMOTE_SSH, CoreFlags.MOVE_TASK)
        val ADVANCED_FLAGS = listOf(CoreFlags.PLUGINS, CoreFlags.AGENT_MESSAGING)
    }
}

/** Opens a file or folder in whatever the OS picks for it. */
internal fun openWithSystem(path: String) {
    val supported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
    if (!supported) throw UnsupportedOperationException("This system cannot open $path from Octo")
    Desktop.getDesktop().open(File(path))
}
