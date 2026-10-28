package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import octo.core.access.Devices
import octo.core.access.Shares
import octo.core.notify.NotificationConfig
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.OctoCore
import octo.core.plugins.Plugin
import octo.core.preset.PresetLibrary
import octo.core.publish.TaskPublisher
import octo.core.task.TaskId
import octo.core.usage.UsageReport
import octo.ui.preview.FilePreviewer

/** Builds the screens' view models, so composables never hold a reference to core. */
class ViewModels(
    private val core: OctoCore,
    private val catalog: AgentCatalog,
    private val scope: CoroutineScope,
    startupWarnings: List<String> = emptyList(),
    /** Null while the `git-publish` flag is off, which hides the action. */
    private val publisher: TaskPublisher? = null,
    /** Null while the `presets` flag is off, which hides teams. */
    private val presets: PresetLibrary? = null,
    /** Null while the `remote-access` flag is off or its server is down, which hides devices. */
    private val devices: Devices? = null,
    private val remoteUrl: String? = null,
    /** Empty while the `plugins` flag is off, which hides plugin actions. */
    private val plugins: List<Plugin> = emptyList(),
    /** False while the `handoff` flag is off, which hides switching a task's agent. */
    private val handoff: Boolean = false,
    /** False while the `fork` flag is off, which hides forking a task. */
    private val fork: Boolean = false,
    /** False while the `compare` flag is off, which hides comparing agents. */
    private val compare: Boolean = false,
    /** False while the `sections` flag is off, which keeps the sidebar one list. */
    sections: Boolean = false,
    /** False while the `palette` flag is off, which leaves Cmd+K unbound. */
    private val palette: Boolean = false,
    /** Null while the `usage` flag is off, which hides usage and limits. */
    private val usage: UsageReport? = null,
    /** False while the `checkpoints` flag is off, which hides reverting a task. */
    private val checkpoints: Boolean = false,
    /** False while the `move-task` flag is off, which hides moving a task to another machine. */
    private val moveTask: Boolean = false,
    /** Null while the `previews` flag is off, which leaves the diff view as plain diffs. */
    private val previewer: FilePreviewer? = null,
    /** Null while the `guest-share` flag is off or remote access is down, which hides sharing. */
    private val shares: Shares? = null,
    /** Where guest links point. */
    private val shareBase: String? = null,
    /** Null while the `notifications` flag is off, which posts no OS notifications. */
    notificationConfig: NotificationConfig? = null,
) {
    val main = MainViewModel(core, scope, startupWarnings, sections, limits = usage != null)

    /** Lives as long as the window, since alerts come whatever screen is open. */
    val notifications = notificationConfig?.let { NotificationsViewModel(core, main, scope, it) }

    val canShowUsage: Boolean get() = usage != null

    val canPublish: Boolean get() = publisher != null

    val canPair: Boolean get() = devices != null && remoteUrl != null

    val canSwitchAgent: Boolean get() = handoff

    val canFork: Boolean get() = fork

    val canMove: Boolean get() = moveTask

    fun task(id: TaskId) = TaskViewModel(core, id, scope, limits = usage != null, checkpoints = checkpoints)

    fun pluginActions(id: TaskId): PluginActionsViewModel? =
        plugins
            .takeIf { all ->
                all.any { it.manifest.actions.isNotEmpty() }
            }?.let { PluginActionsViewModel(it, core, id, scope) }

    fun switchAgent(id: TaskId): SwitchAgentViewModel? =
        if (handoff) SwitchAgentViewModel(core, catalog, id, scope) else null

    fun moveTask(id: TaskId): MoveTaskViewModel? = if (moveTask) MoveTaskViewModel(core, id, scope) else null

    fun share(id: TaskId): ShareViewModel? =
        if (shares != null && shareBase != null) ShareViewModel(shares, id, shareBase, scope) else null

    fun diff(id: TaskId) = DiffViewModel(core, id, scope, previewer)

    /** Null while the `palette` flag is off. Commands behind a flag that is off are left out. */
    fun palette(): PaletteViewModel? {
        if (!palette) return null
        val commands =
            Command.entries.filter {
                when (it) {
                    Command.Publish -> canPublish
                    Command.SwitchAgent -> canSwitchAgent
                    Command.Fork -> canFork
                    Command.Move -> canMove
                    Command.Devices -> canPair
                    Command.Usage -> canShowUsage
                    else -> true
                }
            }
        val selected =
            main.state.value.selected
                ?.let { core.state.value[it]?.task }
        return PaletteViewModel(core, scope, commands, selected)
    }

    fun agents() = AgentsViewModel(core, catalog, scope)

    fun usage(): UsageViewModel? = usage?.let { UsageViewModel(it, catalog, scope) }

    fun newTask() = NewTaskViewModel(core, catalog, scope, presets, compare)

    fun presets(): PresetsViewModel? = presets?.let { PresetsViewModel(it, catalog, scope) }

    fun devices(): DevicesViewModel? =
        if (devices != null && remoteUrl != null) DevicesViewModel(devices, remoteUrl, scope) else null

    fun publish(
        id: TaskId,
        title: String,
    ): PublishViewModel? = publisher?.let { PublishViewModel(it, id, title, scope) }
}
