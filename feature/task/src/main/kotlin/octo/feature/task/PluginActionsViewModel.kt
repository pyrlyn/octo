package octo.feature.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.orchestrator.OctoCore
import octo.core.plugins.Plugin
import octo.core.plugins.PluginTask
import octo.core.task.TaskId
import octo.feature.common.action
import octo.feature.common.childScope
import octo.feature.common.userMessage

/** One entry of the Plugins menu. */
data class PluginActionItem(
    val plugin: String,
    val action: String,
    val title: String,
)

/** What an action asked to show, under the plugin's name. */
data class PluginMessage(
    val plugin: String,
    val text: String,
)

data class PluginActionsUiState(
    val actions: List<PluginActionItem>,
    val running: Boolean = false,
    val message: PluginMessage? = null,
    val error: String? = null,
)

/** The plugin actions of one task: a prompt an action returns goes to the agent, a message is shown. */
class PluginActionsViewModel(
    plugins: List<Plugin>,
    private val core: OctoCore,
    private val taskId: TaskId,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val byId = plugins.associateBy { it.manifest.id }
    private val mutable =
        MutableStateFlow(
            PluginActionsUiState(
                plugins.flatMap { plugin ->
                    plugin.manifest.actions.map {
                        PluginActionItem(
                            plugin.manifest.id,
                            it.id,
                            "${plugin.manifest.name}: ${it.title}",
                        )
                    }
                },
            ),
        )
    val state: StateFlow<PluginActionsUiState> = mutable.asStateFlow()

    fun run(item: PluginActionItem) {
        if (state.value.running) return
        val plugin = byId[item.plugin] ?: return
        mutable.update { it.copy(running = true) }
        scope.action(::fail) {
            try {
                val view = core.state.value[taskId] ?: error("The task was removed")
                val result = plugin.run(item.action, PluginTask.of(view))
                result.prompt?.let { core.sendPrompt(taskId, it) }
                result.message?.let { text ->
                    mutable.update { it.copy(message = PluginMessage(plugin.manifest.name, text)) }
                }
            } finally {
                mutable.update { it.copy(running = false) }
            }
        }
    }

    fun dismissMessage() = mutable.update { it.copy(message = null) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun fail(e: Exception) = mutable.update { it.copy(error = e.userMessage) }
}
