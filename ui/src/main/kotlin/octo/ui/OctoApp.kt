package octo.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import octo.core.config.AppearanceConfig
import octo.core.config.AppearanceTheme
import octo.core.config.RowDensity
import octo.core.config.Transparency
import octo.core.paths.HostOs
import octo.core.paths.currentHostOs
import octo.core.task.TaskId
import octo.design.Banner
import octo.design.Elevation
import octo.design.EmptyState
import octo.design.Gap
import octo.design.GlassBackdrop
import octo.design.GlassLevel
import octo.design.GlassPlatform
import octo.design.Kbd
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.PyrlynTheme
import octo.design.SmallGap
import octo.design.Text
import octo.design.glass
import octo.feature.agents.AgentsScreen
import octo.feature.common.label
import octo.feature.devices.DevicesDialog
import octo.feature.diff.DiffScreen
import octo.feature.newtask.NewTaskDialog
import octo.feature.newtask.summary
import octo.feature.onboarding.OnboardingScreen
import octo.feature.palette.PaletteDialog
import octo.feature.palette.PaletteItem
import octo.feature.settings.SettingsScreen
import octo.feature.task.MoveTaskDialog
import octo.feature.task.PublishDialog
import octo.feature.task.SwitchAgentDialog
import octo.feature.task.TaskScreen
import octo.feature.usage.UsageScreen
import octo.ui.vm.MainUiState
import octo.ui.vm.MainViewModel
import octo.ui.vm.TaskDialog
import octo.ui.vm.ViewModels
import java.awt.Cursor

@Composable
fun OctoApp(models: ViewModels) {
    val main = models.main
    val state by main.state.collectAsState()
    val agents by models.agents.state.collectAsState()
    val summary by models.summary.state.collectAsState()
    models.notifications?.let { vm ->
        val focused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(vm, focused) { vm.focused(focused) }
    }
    ChosenTheme(models) {
        Box(Modifier.fillMaxSize()) {
            GlassBackdrop(Modifier.matchParentSize())
            Row(Modifier.fillMaxSize().padding(Gap)) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val width = dragging ?: state.sidebarWidth
                Sidebar(
                    state,
                    main,
                    agents.rows,
                    Modifier
                        .width(width.dp)
                        .fillMaxHeight()
                        .glass(Pyrlyn.surfaceShape, GlassLevel.Chrome),
                    onDevices = if (models.canPair) main::openDevices else null,
                    onUsage = if (models.canShowUsage) main::openUsage else null,
                    onSettings = if (models.canEditSettings) ({ main.showSettings(true) }) else null,
                )
                // Only the final width is persisted, not every frame of the drag.
                SidebarHandle(
                    onDrag = { delta -> dragging = clampWidth((dragging ?: state.sidebarWidth) + delta) },
                    onDone = {
                        dragging?.let(main::resizeSidebar)
                        dragging = null
                    },
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val search = if (models.canOpenPalette) main::togglePalette else null
                    if (search != null || summary.label != null) Toolbar(summary.label, search)
                    Column(
                        Modifier.fillMaxSize().glass(Pyrlyn.surfaceShape, GlassLevel.Content, Elevation.E3),
                    ) {
                        Banners(state, main)
                        Box(Modifier.weight(1f).fillMaxWidth()) { Content(state, models) }
                    }
                }
            }
            Overlays(state, models)
        }
    }
}

/** The theme with the user's appearance settings applied. */
@Composable
private fun ChosenTheme(
    models: ViewModels,
    content: @Composable () -> Unit,
) {
    val look = appearance(models)
    val dark =
        when (look.theme) {
            AppearanceTheme.System -> isSystemInDarkTheme()
            AppearanceTheme.Light -> false
            AppearanceTheme.Dark -> true
        }
    val platform = remember(look.density) { glassPlatform(currentHostOs(), look.density) }
    PyrlynTheme(dark, platform, opaque = look.transparency == Transparency.Opaque, content = content)
}

/** The design system knows recipes, not host OSes or config values; the shell picks one. */
private fun glassPlatform(
    os: HostOs,
    density: RowDensity,
): GlassPlatform =
    when (os) {
        HostOs.MacOs -> GlassPlatform.MacOS
        HostOs.Windows -> GlassPlatform.Windows
        HostOs.Linux -> GlassPlatform.Linux
    }.withRowStep(density.stepDp.dp)

@Composable
private fun Overlays(
    state: MainUiState,
    models: ViewModels,
) {
    val main = models.main
    if (state.newTaskOpen) {
        val vm =
            remember {
                models.newTask().also { vm ->
                    state.newTaskDraft?.let {
                        vm.editRepo(it.repo)
                        it.agent?.let(vm::selectAgent)
                        vm.selectPermissionMode(it.mode)
                    }
                }
            }
        DisposableEffect(vm) { onDispose { vm.close() } }
        NewTaskDialog(vm, onCreated = main::created, onDismiss = main::closeNewTask, presets = models::presets)
    }
    if (state.devicesOpen) DevicesPane(models, onDismiss = main::closeDevices)
    if (state.paletteOpen) PalettePane(models)
    models.onboarding?.let { vm ->
        DisposableEffect(vm) { onDispose { vm.close() } }
        OnboardingScreen(vm) { draft -> draft?.let(main::openNewTask) }
    }
}

@Composable
private fun Banners(
    state: MainUiState,
    main: MainViewModel,
) {
    if (state.warnings.isEmpty() && state.error == null) return
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(Gap)) {
        state.warnings.forEach { Banner(it, error = false, onDismiss = main::dismissWarnings) }
        state.error?.let { Banner(it, error = true, onDismiss = main::dismissError) }
    }
}

/** The bar over the content panel: today's summary and a search field that opens the command palette. */
@Composable
private fun Toolbar(
    summary: String?,
    onSearch: (() -> Unit)?,
) {
    val c = Pyrlyn.colors
    Row(
        Modifier.fillMaxWidth().height(ToolbarHeight).padding(horizontal = Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(summary.orEmpty(), Modifier.weight(1f), style = Pyrlyn.type.small, color = c.fgMuted, maxLines = 1)
        if (onSearch != null) SearchField(onSearch)
    }
}

@Composable
private fun SearchField(onSearch: () -> Unit) {
    val c = Pyrlyn.colors
    Row(
        Modifier
            .width(SearchWidth)
            .height(Pyrlyn.platform.controlHeight)
            .glass(Pyrlyn.controlShape, GlassLevel.Control, Elevation.E1)
            .clickable(onClick = onSearch)
            .padding(start = 10.dp, end = SmallGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text("⌕", color = c.fgSubtle)
        Text("Search tasks, agents, commands", Modifier.weight(1f), color = c.fgSubtle, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Kbd(if (Pyrlyn.platform == GlassPlatform.MacOS) "⌘" else "Ctrl")
            Kbd("K")
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
    )
}

@Composable
private fun Content(
    state: MainUiState,
    models: ViewModels,
) {
    val id = state.selected
    when {
        state.agentsOpen -> AgentsPane(models)
        state.usageOpen -> UsagePane(models)
        state.settingsOpen && models.settings != null -> SettingsPane(models)
        id == null -> Empty(state.groups.isEmpty(), models.main::openNewTask)
        else -> TaskOrDiff(id, state.diffOpen, models)
    }
}

@Composable
private fun TaskOrDiff(
    id: TaskId,
    diffOpen: Boolean,
    models: ViewModels,
) {
    key(id, diffOpen) {
        if (diffOpen) {
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
    val share = remember(id) { models.share(id) }
    DisposableEffect(share) { onDispose { share?.close() } }
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
        onMove = if (models.canMove) ({ main.openTaskDialog(TaskDialog.Move) }) else null,
        places = models.main.takeIf { it.sections },
        share = share,
    )
    if (dialog == TaskDialog.SwitchAgent) {
        val switch = remember(id) { models.switchAgent(id) } ?: return
        DisposableEffect(switch) { onDispose { switch.close() } }
        SwitchAgentDialog(switch, onDismiss = main::closeTaskDialog)
    }
    if (dialog == TaskDialog.Move) MovePane(id, models)
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
private fun MovePane(
    id: TaskId,
    models: ViewModels,
) {
    val move = remember(id) { models.moveTask(id) } ?: return
    DisposableEffect(move) { onDispose { move.close() } }
    MoveTaskDialog(move, onDismiss = models.main::closeTaskDialog)
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
    AgentsScreen(models.agents, onClose = models.main::closeAgents)
}

@Composable
private fun UsagePane(models: ViewModels) {
    val vm = remember { models.usage() } ?: return
    DisposableEffect(vm) { onDispose { vm.close() } }
    UsageScreen(vm, onClose = models.main::closeUsage)
}

@Composable
private fun SettingsPane(models: ViewModels) {
    val vm = models.settings ?: return
    // config.json may have changed since the window opened: onboarding, or an editor.
    LaunchedEffect(vm) { vm.reload() }
    SettingsScreen(
        vm,
        onClose = { models.main.showSettings(false) },
        onPair = if (models.canPair) models.main::openDevices else null,
    )
}

/** The look the user chose in settings; the defaults until `config.json` is read. */
@Composable
private fun appearance(models: ViewModels): AppearanceConfig {
    val vm = models.settings ?: return AppearanceConfig()
    return vm.state
        .collectAsState()
        .value.config.appearance
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
private fun Empty(
    firstRun: Boolean,
    onNew: () -> Unit,
) {
    EmptyState(
        icon = "◎",
        title = if (firstRun) "Start your first task" else "No task selected",
        body =
            if (firstRun) {
                "Octo runs each agent in its own git worktree."
            } else {
                "Pick a task in the sidebar or start a new one."
            },
    ) { PrimaryButton(onClick = onNew) { Text("+  New task") } }
}

private val ToolbarHeight = 44.dp
private val DividerWidth = 8.dp
private val SearchWidth = 340.dp
