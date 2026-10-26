package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId

/** Builds the screens' view models, so composables never hold a reference to core. */
class ViewModels(
    private val core: OctoCore,
    private val catalog: AgentCatalog,
    private val scope: CoroutineScope,
    startupWarnings: List<String> = emptyList(),
) {
    val main = MainViewModel(core, scope, startupWarnings)

    fun task(id: TaskId) = TaskViewModel(core, id, scope)

    fun diff(id: TaskId) = DiffViewModel(core, id, scope)

    fun newTask() = NewTaskViewModel(core, catalog, scope)
}
