package octo.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import octo.core.task.TaskId
import octo.ui.vm.MainUiState
import octo.ui.vm.PaletteItem
import octo.ui.vm.TaskDialog
import octo.ui.vm.ViewModels
import java.awt.Cursor

@Composable
fun OctoApp(models: ViewModels) {
    val main = models.main
    val state by main.state.collectAsState()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                state.warnings.forEach { Banner(it, error = false, onDismiss = main::dismissWarnings) }
                state.error?.let { Banner(it, error = true, onDismiss = main::dismissError) }
                Row(Modifier.fillMaxSize()) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val width = dragging ?: state.sidebarWidth
                    Sidebar(
                        state,
                        main,
                        Modifier.width(width.dp).fillMaxHeight(),
                        onDevices = if (models.canPair) main::openDevices else null,
                    )
                    // Only the final width is persisted, not every frame of the drag.
                    SidebarHandle(
                        onDrag = { delta -> dragging = clampWidth((dragging ?: state.sidebarWidth) + delta) },
                        onDone = {
                            dragging?.let(main::resizeSidebar)
                            dragging = null
                        },
                    )
                    Box(Modifier.weight(1f).fillMaxHeight()) { Content(state, models) }
                }
            }
            if (state.newTaskOpen) {
                val vm = remember { models.newTask() }
                DisposableEffect(vm) { onDispose { vm.close() } }
                NewTaskDialog(vm, onCreated = main::created, onDismiss = main::closeNewTask, presets = models::presets)
            }
            if (state.devicesOpen) DevicesPane(models, onDismiss = main::closeDevices)
            if (state.paletteOpen) PalettePane(models)
        }
    }
}

private fun clampWidth(width: Float) = width.coerceIn(MainUiState.MIN_SIDEBAR_WIDTH, MainUiState.MAX_SIDEBAR_WIDTH)

@Composable
private fun SidebarHandle(
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .width(DividerWidth)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                rememberDraggableState { px -> onDrag(px / density) },
                Orientation.Horizontal,
                onDragStopped = { onDone() },
            ),
        contentAlignment = Alignment.Center,
    ) { VerticalDivider() }
}

@Composable
private fun Content(
    state: MainUiState,
    models: ViewModels,
) {
    if (state.agentsOpen) {
        AgentsPane(models)
        return
    }
    val id = state.selected
    if (id == null) {
        Empty(models.main::openNewTask)
        return
    }
    key(id, state.diffOpen) {
        if (state.diffOpen) {
            DiffPane(id, models)
        } else {
            TaskPane(id, models)
        }
    }
}

@Composable
private fun TaskPane(
    id: TaskId,
    models: ViewModels,
) {
    val vm = remember(id) { models.task(id) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    val plugins = remember(id) { models.pluginActions(id) }
    DisposableEffect(plugins) { onDispose { plugins?.close() } }
    val main = models.main
    val dialog =
        main.state
            .collectAsState()
            .value.taskDialog
    TaskScreen(
        vm,
        onDiff = { main.showDiff(true) },
        onPublish = if (models.canPublish) ({ main.openTaskDialog(TaskDialog.Publish) }) else null,
        plugins = plugins,
        onSwitchAgent = if (models.canSwitchAgent) ({ main.openTaskDialog(TaskDialog.SwitchAgent) }) else null,
        onFork = if (models.canFork) ({ models.main.fork(id) }) else null,
        places = models.main.takeIf { it.sections },
    )
    if (dialog == TaskDialog.SwitchAgent) {
        val switch = remember(id) { models.switchAgent(id) } ?: return
        DisposableEffect(switch) { onDispose { switch.close() } }
        SwitchAgentDialog(switch, onDismiss = main::closeTaskDialog)
    }
    if (dialog == TaskDialog.Publish) {
        val title =
            vm.state.value.task
                ?.title
                .orEmpty()
        val publish = remember(id) { models.publish(id, title) } ?: return
        DisposableEffect(publish) { onDispose { publish.close() } }
        PublishDialog(publish, onDismiss = main::closeTaskDialog)
    }
}

@Composable
private fun PalettePane(models: ViewModels) {
    val vm = remember { models.palette() } ?: return
    DisposableEffect(vm) { onDispose { vm.close() } }
    val main = models.main
    PaletteDialog(vm, onDismiss = main::closePalette) { item ->
        main.closePalette()
        when (item) {
            is PaletteItem.Open -> main.select(item.id)
            is PaletteItem.Run -> main.run(item.command)
        }
    }
}

@Composable
private fun AgentsPane(models: ViewModels) {
    val vm = remember { models.agents() }
    DisposableEffect(vm) { onDispose { vm.close() } }
    AgentsScreen(vm, onClose = models.main::closeAgents)
}

@Composable
private fun DevicesPane(
    models: ViewModels,
    onDismiss: () -> Unit,
) {
    val vm = remember { models.devices() } ?: return
    DisposableEffect(vm) { onDispose { vm.close() } }
    DevicesDialog(vm, onDismiss)
}

@Composable
private fun DiffPane(
    id: TaskId,
    models: ViewModels,
) {
    val vm = remember(id) { models.diff(id) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    DiffScreen(vm, onClose = { models.main.showDiff(false) })
}

@Composable
private fun Empty(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No task selected", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onNew, modifier = Modifier.padding(top = Gap)) { Text("New task") }
    }
}

@Composable
internal fun Banner(
    text: String,
    error: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (error) colors.errorContainer else colors.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = LargeGap, vertical = SmallGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f), color = if (error) colors.onErrorContainer else colors.onSecondaryContainer)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
