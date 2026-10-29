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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import octo.core.config.AppearanceConfig
import octo.core.config.AppearanceTheme
import octo.core.config.RowDensity
import octo.core.config.Transparency
import octo.core.paths.HostOs
import octo.core.task.TaskId
import octo.design.Banner
import octo.design.CaptionButtons
import octo.design.CloseCircle
import octo.design.Elevation
import octo.design.EmptyState
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassBackdrop
import octo.design.GlassLevel
import octo.design.GlassMenu
import octo.design.GlassMenuItem
import octo.design.GlassPlatform
import octo.design.Kbd
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.PyrlynTheme
import octo.design.SecondaryButton
import octo.design.SmallGap
import octo.design.Text
import octo.design.chromeVeil
import octo.design.glass
import octo.feature.agents.AgentRow
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
import octo.ui.window.Chrome
import octo.ui.window.SystemLook
import octo.ui.window.WindowFrame
import java.awt.Cursor

@Composable
fun OctoApp(
    models: ViewModels,
    frame: WindowFrame = WindowFrame(),
    look: SystemLook = SystemLook(),
) {
    val main = models.main
    val state by main.state.collectAsState()
    val agents by models.agents.state.collectAsState()
    val summary by models.summary.state.collectAsState()
    models.notifications?.let { vm ->
        val focused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(vm, focused) { vm.focused(focused) }
    }
    ChosenTheme(models, frame, look) {
        Box(Modifier.fillMaxSize()) {
            // A native material already shows the desktop through the window.
            if (!look.material || Pyrlyn.opaque) GlassBackdrop(Modifier.matchParentSize())
            val search = if (models.canOpenPalette) main::togglePalette else null
            // Windows and Linux draw their own bar across the top, and the sidebar runs flush under it.
            val flush = frame.chrome == Chrome.Caption || frame.chrome == Chrome.HeaderBar
            Column(Modifier.fillMaxSize()) {
                when (frame.chrome) {
                    Chrome.Caption -> CaptionBar(frame)
                    Chrome.HeaderBar -> HeaderBar(frame, state, models, search)
                    Chrome.System, Chrome.MacOs -> Unit
                }
                Row(Modifier.weight(1f).fillMaxWidth().then(if (flush) Modifier else Modifier.padding(Gap))) {
                    SidebarPane(state, models, agents.rows, frame, flush)
                    Workspace(state, models, summary.label, search, frame, flush)
                }
            }
            Overlays(state, models)
        }
    }
}

/** The sidebar and the handle that resizes it. */
@Composable
private fun RowScope.SidebarPane(
    state: MainUiState,
    models: ViewModels,
    agents: List<AgentRow>,
    frame: WindowFrame,
    flush: Boolean,
) {
    val main = models.main
    var dragging by remember { mutableStateOf<Float?>(null) }
    val width = dragging ?: state.sidebarWidth
    val surface = if (flush) Modifier.chromeVeil() else Modifier.glass(Pyrlyn.surfaceShape, GlassLevel.Chrome)
    Sidebar(
        state,
        main,
        agents,
        Modifier.width(width.dp).fillMaxHeight().then(surface),
        onDevices = if (models.canPair) main::openDevices else null,
        onUsage = if (models.canShowUsage) main::openUsage else null,
        onSettings = if (models.canEditSettings) ({ main.showSettings(true) }) else null,
        top = {
            // The traffic lights sit over this strip.
            if (frame.chrome == Chrome.MacOs) frame.dragArea(Modifier.fillMaxWidth().height(LightsHeight)) {}
        },
    )
    // Only the final width is persisted, not every frame of the drag.
    SidebarHandle(
        onDrag = { delta -> dragging = clampWidth((dragging ?: state.sidebarWidth) + delta) },
        onDone = {
            dragging?.let(main::resizeSidebar)
            dragging = null
        },
    )
}

/** The toolbar over the content panel, and the panel. */
@Composable
private fun RowScope.Workspace(
    state: MainUiState,
    models: ViewModels,
    summary: String?,
    search: (() -> Unit)?,
    frame: WindowFrame,
    flush: Boolean,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(if (flush) Modifier.padding(end = Gap, bottom = Gap) else Modifier),
    ) {
        // Linux keeps search in its header bar and has no toolbar.
        if (frame.chrome != Chrome.HeaderBar && (search != null || summary != null)) {
            frame.dragArea(Modifier.fillMaxWidth()) {
                Toolbar(summary, search, if (flush) CaptionToolbarHeight else ToolbarHeight)
            }
        }
        Column(Modifier.fillMaxSize().glass(Pyrlyn.surfaceShape, GlassLevel.Content, Elevation.E3)) {
            Banners(state, models.main)
            Box(Modifier.weight(1f).fillMaxWidth()) { Content(state, models) }
        }
    }
}

/** The theme with the user's appearance settings and the OS's accessibility settings applied. */
@Composable
private fun ChosenTheme(
    models: ViewModels,
    frame: WindowFrame,
    look: SystemLook,
    content: @Composable () -> Unit,
) {
    val chosen = appearance(models)
    val dark =
        when (chosen.theme) {
            AppearanceTheme.System -> isSystemInDarkTheme()
            AppearanceTheme.Light -> false
            AppearanceTheme.Dark -> true
        }
    LaunchedEffect(dark) { frame.onDarkChanged(dark) }
    val platform = remember(frame.os, chosen.density) { glassPlatform(frame.os, chosen.density) }
    PyrlynTheme(
        dark,
        platform,
        opaque = chosen.transparency == Transparency.Opaque || look.reducedTransparency,
        reducedMotion = look.reducedMotion,
        content = content,
    )
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
    height: Dp,
) {
    val c = Pyrlyn.colors
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(summary.orEmpty(), Modifier.weight(1f), style = Pyrlyn.type.small, color = c.fgMuted, maxLines = 1)
        if (onSearch != null) SearchField(onSearch, SearchWidth, "Search tasks, agents, commands")
    }
}

@Composable
internal fun SearchField(
    onSearch: () -> Unit,
    width: Dp,
    placeholder: String,
) {
    val c = Pyrlyn.colors
    Row(
        Modifier
            .width(width)
            .height(Pyrlyn.platform.controlHeight)
            .glass(Pyrlyn.controlShape, GlassLevel.Control, Elevation.E1)
            .clickable(onClick = onSearch)
            .padding(start = 10.dp, end = SmallGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text("⌕", color = c.fgSubtle)
        Text(placeholder, Modifier.weight(1f), color = c.fgSubtle, maxLines = 1)
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
private val CaptionToolbarHeight = 48.dp

// macOS's unified title bar is 52pt tall, and the sidebar starts a gap below the window's top.
private val LightsHeight = 44.dp
private val DividerWidth = 8.dp
private val SearchWidth = 340.dp
