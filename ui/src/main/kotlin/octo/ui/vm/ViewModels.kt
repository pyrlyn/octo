package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.OctoCore
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
) {
    val main = MainViewModel(core, scope, startupWarnings)

    val canPublish: Boolean get() = publisher != null

    fun task(id: TaskId) = TaskViewModel(core, id, scope)

    fun diff(id: TaskId) = DiffViewModel(core, id, scope)

    fun newTask() = NewTaskViewModel(core, catalog, scope, presets)

    fun presets(): PresetsViewModel? = presets?.let { PresetsViewModel(it, catalog, scope) }

    fun publish(
        id: TaskId,
        title: String,
    ): PublishViewModel? = publisher?.let { PublishViewModel(it, id, title, scope) }
}
