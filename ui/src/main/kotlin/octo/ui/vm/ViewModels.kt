package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.OctoCore
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
) {
    val main = MainViewModel(core, scope, startupWarnings)

    val canPublish: Boolean get() = publisher != null

    fun task(id: TaskId) = TaskViewModel(core, id, scope)

    fun diff(id: TaskId) = DiffViewModel(core, id, scope)

    fun newTask() = NewTaskViewModel(core, catalog, scope)

    fun publish(
        id: TaskId,
        title: String,
    ): PublishViewModel? = publisher?.let { PublishViewModel(it, id, title, scope) }
}
