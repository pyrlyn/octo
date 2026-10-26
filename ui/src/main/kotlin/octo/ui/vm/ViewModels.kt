package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import octo.core.access.Devices
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.OctoCore
import octo.core.plugins.Plugin
import octo.core.preset.PresetLibrary
import octo.core.publish.TaskPublisher
import octo.core.task.TaskId

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
) {
    val main = MainViewModel(core, scope, startupWarnings)

    val canPublish: Boolean get() = publisher != null

    val canPair: Boolean get() = devices != null && remoteUrl != null

    fun task(id: TaskId) = TaskViewModel(core, id, scope)

    fun pluginActions(id: TaskId): PluginActionsViewModel? =
        plugins
            .takeIf { all ->
                all.any { it.manifest.actions.isNotEmpty() }
            }?.let { PluginActionsViewModel(it, core, id, scope) }

    fun diff(id: TaskId) = DiffViewModel(core, id, scope)

    fun newTask() = NewTaskViewModel(core, catalog, scope, presets)

    fun presets(): PresetsViewModel? = presets?.let { PresetsViewModel(it, catalog, scope) }

    fun devices(): DevicesViewModel? =
        if (devices != null && remoteUrl != null) DevicesViewModel(devices, remoteUrl, scope) else null

    fun publish(
        id: TaskId,
        title: String,
    ): PublishViewModel? = publisher?.let { PublishViewModel(it, id, title, scope) }
}
