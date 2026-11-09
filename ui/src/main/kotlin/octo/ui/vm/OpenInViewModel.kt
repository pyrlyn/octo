package octo.ui.vm

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import octo.core.config.ConfigEdit
import octo.core.config.ConfigFile
import octo.core.orchestrator.OctoCore
import octo.feature.common.AppOpener
import octo.feature.common.OpenApp
import octo.feature.common.action
import octo.feature.common.appIcon
import octo.feature.common.childScope
import octo.feature.common.userMessage
import kotlin.coroutines.cancellation.CancellationException

data class OpenInUiState(
    /** The selected task's worktree. */
    val folder: String,
    val apps: List<OpenApp>,
    /** What the button opens: the app used last, or the file manager until one is. */
    val main: OpenApp,
    /** By app id; an app without one shows a glyph for its kind. */
    val icons: Map<String, ImageBitmap> = emptyMap(),
)

/**
 * The toolbar's "Open location" button. Its state is null, which hides the button, while no task is selected or the
 * selected one runs on another machine, whose folders no app here can open.
 */
class OpenInViewModel(
    private val opener: AppOpener,
    core: OctoCore,
    main: MainViewModel,
    private val config: ConfigFile?,
    parent: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val iconOf: (String) -> ImageBitmap? = { appIcon(it, ICON_PIXELS) },
) {
    private val scope = parent.childScope()
    private val apps = MutableStateFlow<List<OpenApp>>(emptyList())
    private val last = MutableStateFlow<String?>(null)
    private val failure = MutableStateFlow<String?>(null)
    private val icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** Why the last app would not open, until dismissed. */
    val error: StateFlow<String?> = failure

    val state: StateFlow<OpenInUiState?> =
        combine(main.state, core.state, apps, last, icons) { ui, octo, found, lastId, images ->
            val task = ui.selected?.let { octo[it]?.task }?.takeIf { it.machine == null }
            val first = found.firstOrNull()
            if (task == null || first == null) {
                null
            } else {
                OpenInUiState(task.worktree, found, found.firstOrNull { it.id == lastId } ?: first, images)
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            // Finding the apps, reading the config and the icons all touch the disk.
            withContext(io) {
                val found = opener.installed()
                apps.value = found
                last.value = quietly { config?.load()?.config?.openIn }
                icons.value = found.mapNotNull { app -> app.icon?.let(iconOf)?.let { app.id to it } }.toMap()
            }
        }
    }

    fun open(app: OpenApp) {
        val folder = state.value?.folder ?: return
        failure.value = null
        scope.action({ failure.value = "Could not open ${app.name}: ${it.userMessage}" }) {
            withContext(io) { opener.open(app, folder) }
            // Only an app that opened becomes the button's, so a broken one does not stick.
            quietly { config?.update(ConfigEdit.openIn(app.id)) }
            last.value = app.id
        }
    }

    fun dismissError() {
        failure.value = null
    }

    fun close() = scope.cancel()

    // The button works without the saved choice, so a config that cannot be read or written only costs that.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> quietly(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}

// Twice the 16 dp the menu draws, for Retina screens.
private const val ICON_PIXELS = 32
