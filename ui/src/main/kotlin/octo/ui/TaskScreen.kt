package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.core.agent.PermissionMode
import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.task.TranscriptEntry
import octo.ui.theme.Elevation
import octo.ui.theme.GlassLevel
import octo.ui.theme.Pyrlyn
import octo.ui.theme.glass
import octo.ui.vm.MainViewModel
import octo.ui.vm.PluginActionsViewModel
import octo.ui.vm.RemoveStep
import octo.ui.vm.ShareViewModel
import octo.ui.vm.TaskUiState
import octo.ui.vm.TaskViewModel

@Composable
internal fun TaskScreen(
    vm: TaskViewModel,
    onDiff: () -> Unit,
    onPublish: (() -> Unit)? = null,
    /** Null without plugin actions, which hides the menu. */
    plugins: PluginActionsViewModel? = null,
    /** Null while the `handoff` flag is off, which hides the action. */
    onSwitchAgent: (() -> Unit)? = null,
    /** Null while the `fork` flag is off, which hides the action. */
    onFork: (() -> Unit)? = null,
    /** Null while the `move-task` flag is off, which hides the action. */
    onMove: (() -> Unit)? = null,
    /** Null while the `sections` flag is off, which hides pinning, snoozing and settling. */
    places: MainViewModel? = null,
    /** Null while the `guest-share` flag is off, which hides sharing. */
    share: ShareViewModel? = null,
) {
    val state by vm.state.collectAsState()
    val task = state.task ?: return
    Column(Modifier.fillMaxSize()) {
        // Publishing runs this computer's git and gh, which cannot reach a remote worktree.
        Header(
            state,
            vm,
            onDiff,
            onPublish.takeIf { task.machine == null },
            plugins,
            listOfNotNull(
                onSwitchAgent?.let { "Switch agent" to it },
                onFork?.let { "Fork" to it },
                onMove?.let { "Move to…" to it },
            ),
            places,
            share,
        )
        Hairline()
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = PanePadding, end = PanePadding, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            state.failure?.let { Notice(it, Tone.Error) }
            state.limit?.let { LimitBanner(it, onResume = vm::resume) }
            state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
            Transcript(state, vm, Modifier.weight(1f).fillMaxWidth())
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = LargeGap, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Queue(state, vm)
            state.pending.forEach {
                RequestPanel(it, state.answers[it.id].orEmpty(), state.answerFiles[it.id].orEmpty(), vm)
            }
            share?.let { GuestProposals(it) }
            PromptBox(state, vm)
        }
    }
    RemoveDialog(state.remove, vm)
    state.reverting?.let { RevertDialog(it, vm) }
}

@Composable
private fun Header(
    state: TaskUiState,
    vm: TaskViewModel,
    onDiff: () -> Unit,
    onPublish: (() -> Unit)?,
    plugins: PluginActionsViewModel?,
    /** Actions that end or copy the running turn's work, so they wait until the agent is done. */
    turnActions: List<Pair<String, () -> Unit>>,
    places: MainViewModel?,
    share: ShareViewModel?,
) {
    val task = state.task ?: return
    val status = state.sidebarStatus
    val where = task.machine?.let { "${task.branch} · on $it" } ?: task.branch
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PanePadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = Pyrlyn.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(status)
                    Text(
                        "${task.agent.value} · ${status.label} · $where",
                        style = Pyrlyn.type.small,
                        color = Pyrlyn.colors.fgMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.canCancel) SecondaryButton(onClick = vm::cancel) { Text("Cancel turn") }
            if (state.canStartNow) SecondaryButton(onClick = vm::startNow) { Text("Start now") }
            if (state.canStop) SecondaryButton(onClick = vm::stop) { Text("Stop") }
            if (state.canRestart) SecondaryButton(onClick = vm::restart) { Text("Restart") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
            PermissionMenu(task.permissionMode, vm::setPermissionMode)
            state.context?.let { ContextMenu(it) }
            share?.let { ShareButton(it) }
            Spacer(Modifier.weight(1f))
            // A switch or a move ends the running turn, and a fork taken mid-turn would copy half-written files.
            turnActions.forEach { (label, action) ->
                GhostButton(onClick = action, enabled = !state.canCancel) { Text(label) }
            }
            GhostButton(onClick = onDiff) { Text("±  Diff") }
            // Publishing while the agent writes would push half a change.
            onPublish?.let { PrimaryButton(onClick = it, enabled = !state.canCancel) { Text("↑  Publish") } }
            plugins?.let { PluginMenu(it) }
            places?.let { PlaceMenu(task.id, task.place, it) }
            GhostButton(onClick = vm::requestRemove) { Text("Remove") }
        }
    }
}

/** Every mode is offered: an agent without the chosen one says so in the transcript and runs Supervised. */
@Composable
private fun PermissionMenu(
    current: PermissionMode,
    onSelect: (PermissionMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        GhostButton(onClick = { open = true }, selected = true) { Text("◐  Permissions: ${current.label}") }
        GlassMenu(open, onDismissRequest = { open = false }) {
            PermissionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (mode == current) "✓  " else "    ") + mode.label,
                            color = if (mode == PermissionMode.FullAccess) Pyrlyn.colors.danger else Color.Unspecified,
                        )
                    },
                    onClick = {
                        open = false
                        if (mode != current) onSelect(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun Transcript(
    state: TaskUiState,
    vm: TaskViewModel,
    modifier: Modifier,
) {
    val entries = state.transcript
    val list = rememberLazyListState()
    // Follow the live output like a terminal does; tool updates and streamed text change the last
    // entry in place, so its content is a key too.
    LaunchedEffect(entries.size, entries.lastOrNull()) {
        if (entries.isNotEmpty()) list.scrollToItem(entries.lastIndex)
    }
    SelectionContainer(modifier) {
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(Gap)) {
            itemsIndexed(entries) { _, entry ->
                if (entry is TranscriptEntry.Checkpoint) {
                    // Reverting under a running turn would fight the agent for the files.
                    CheckpointRow(entry, entry.ref in state.revertable, enabled = !state.busy, vm::askRevert)
                } else {
                    Entry(entry)
                }
            }
        }
    }
}

@Composable
private fun Entry(entry: TranscriptEntry) {
    val c = Pyrlyn.colors
    when (entry) {
        is TranscriptEntry.Text -> {
            Message(entry)
        }

        is TranscriptEntry.Thought -> {
            Collapsible("Thinking", entry.text, FontStyle.Italic)
        }

        is TranscriptEntry.Tool -> {
            Card {
                Collapsible(
                    "${entry.title} · ${entry.status.label}",
                    entry.detail,
                    mono = true,
                    error = entry.status == ToolStatus.Failed,
                )
            }
        }

        is TranscriptEntry.Plan -> {
            Card(Modifier.border(0.5.dp, c.glassEdge, CardShape)) {
                Text("Plan", style = Pyrlyn.type.small, color = c.fgMuted, fontWeight = FontWeight.SemiBold)
                entry.entries.forEach {
                    Text((if (it.done) "✓  " else "○  ") + it.text, color = if (it.done) c.fgMuted else c.fg)
                }
            }
        }

        is TranscriptEntry.Request -> {
            Column {
                Text(
                    "⏸ Asked: ${entry.title}" + if (entry.resolved) " (answered)" else "",
                    style = Pyrlyn.type.small,
                    color = c.fgMuted,
                )
                AttachedFiles(entry.attachments)
            }
        }

        is TranscriptEntry.Notice -> {
            Text(entry.text, style = Pyrlyn.type.small, color = if (entry.error) c.danger else c.fgMuted)
        }

        is TranscriptEntry.Checkpoint -> {
            Text("Checkpoint ${entry.id}", style = Pyrlyn.type.small, color = c.fgSubtle)
        }
    }
}

@Composable
private fun Message(entry: TranscriptEntry.Text) {
    val c = Pyrlyn.colors
    val user = entry.role == Role.User
    val frame =
        if (user) {
            Modifier.fillMaxWidth().background(c.accentBg.copy(alpha = USER_TINT), CardShape).padding(10.dp)
        } else {
            Modifier.padding(horizontal = 2.dp)
        }
    Column(Modifier.alpha(if (entry.partial) PARTIAL_ALPHA else 1f).then(frame)) {
        Text(
            if (user) "You" else "Agent",
            style = Pyrlyn.type.small,
            color = if (user) c.accentFg else c.fgMuted,
            fontWeight = FontWeight.SemiBold,
        )
        // A message can be files alone, and an empty line above them would look like a lost text.
        if (entry.text.isNotEmpty()) Text(entry.text)
        AttachedFiles(entry.attachments)
    }
}

/** A point the worktree can go back to; one that was dropped or belongs to another task stays a label. */
@Composable
private fun CheckpointRow(
    entry: TranscriptEntry.Checkpoint,
    revertable: Boolean,
    enabled: Boolean,
    onRevert: (TranscriptEntry.Checkpoint) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Hairline(Modifier.weight(1f))
        Text("Checkpoint ${entry.id}", style = Pyrlyn.type.small, color = Pyrlyn.colors.fgSubtle)
        if (revertable) {
            GhostButton(onClick = { onRevert(entry) }, enabled = enabled, contentColor = Pyrlyn.colors.accentFg) {
                Text("Revert to here", style = Pyrlyn.type.small)
            }
        }
        Hairline(Modifier.weight(1f))
    }
}

@Composable
private fun RevertDialog(
    checkpoint: TranscriptEntry.Checkpoint,
    vm: TaskViewModel,
) {
    GlassDialog(
        onDismissRequest = { vm.askRevert(null) },
        title = { Text("Revert to checkpoint ${checkpoint.id}?") },
        text = {
            Text(
                "The worktree's files go back to how they were here, and later checkpoints are dropped. " +
                    "Changes made since, including uncommitted ones, are lost. The branch keeps its commits, so " +
                    "files a later commit changed show as uncommitted.",
            )
        },
        confirmButton = { PrimaryButton(onClick = vm::revert) { Text("Revert") } },
        dismissButton = { GhostButton(onClick = { vm.askRevert(null) }) { Text("Cancel") } },
    )
}

/** Prompts waiting for the agent, each editable in place until it is sent. */
@Composable
private fun Queue(
    state: TaskUiState,
    vm: TaskViewModel,
) {
    val c = Pyrlyn.colors
    state.queue.forEach { queued ->
        val editing = state.editing?.takeIf { it.id == queued.id }
        Row(
            Modifier
                .fillMaxWidth()
                .glass(CardShape, GlassLevel.Floating, Elevation.E1)
                .padding(start = 12.dp, end = SmallGap, top = SmallGap, bottom = SmallGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text("Queued", style = Pyrlyn.type.small, color = c.fgMuted, fontWeight = FontWeight.SemiBold)
            if (editing == null) {
                val files =
                    queued.attachments
                        .takeIf { it.isNotEmpty() }
                        ?.let { " · ${it.label()}" }
                        .orEmpty()
                Text(
                    queued.text + files,
                    Modifier.weight(1f),
                    style = Pyrlyn.type.small,
                    color = c.fgMuted,
                    maxLines = 2,
                )
                GhostButton(onClick = { vm.editQueued(queued) }, contentColor = c.accentFg) { Text("Edit") }
            } else {
                GlassField(editing.text, vm::editQueuedText, Modifier.weight(1f), maxLines = 4)
                PrimaryButton(onClick = vm::saveQueued, enabled = editing.text.isNotBlank()) { Text("Save") }
                GhostButton(onClick = vm::cancelQueuedEdit) { Text("Cancel") }
            }
            GhostButton(onClick = { vm.removeQueued(queued.id) }, contentColor = c.fgMuted) { Text("Remove") }
        }
    }
}

/** A one-line header; thoughts and tool output run to pages, so the body opens on a click. */
@Composable
private fun Collapsible(
    header: String,
    body: String?,
    bodyStyle: FontStyle = FontStyle.Normal,
    /** Tool output: monospaced, with an accent header. */
    mono: Boolean = false,
    error: Boolean = false,
) {
    val c = Pyrlyn.colors
    var open by remember { mutableStateOf(false) }
    val expandable = !body.isNullOrBlank()
    val marker =
        when {
            !expandable -> "  "
            open -> "▾ "
            else -> "▸ "
        }
    val style = if (mono) Pyrlyn.type.mono else Pyrlyn.type.body
    Column {
        Text(
            marker + header,
            style = style,
            color = if (mono) c.accentFg else c.fgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (expandable) Modifier.clickable { open = !open } else Modifier,
        )
        if (open && body != null) {
            Text(
                body,
                color = if (error) c.danger else c.fgMuted,
                style = style,
                fontStyle = bodyStyle,
                modifier = Modifier.padding(start = LargeGap),
            )
        }
    }
}

private val ToolStatus.label: String
    get() =
        when (this) {
            ToolStatus.Pending -> "pending"
            ToolStatus.InProgress -> "running"
            ToolStatus.Completed -> "done"
            ToolStatus.Failed -> "failed"
        }

@Composable
private fun PromptBox(
    state: TaskUiState,
    vm: TaskViewModel,
) {
    val c = Pyrlyn.colors
    val input = remember(vm) { AttachmentInput(vm::attach, vm::showError) }
    PendingAttachments(state.attachments, vm::detach)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(Pyrlyn.surfaceShape, GlassLevel.Floating, Elevation.E3)
            .padding(end = Gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        GlassField(
            value = state.draft,
            onValueChange = vm::editDraft,
            modifier =
                Modifier
                    .weight(1f)
                    .onPreviewKeyEvent {
                        val send =
                            it.type == KeyEventType.KeyDown &&
                                it.key == Key.Enter &&
                                (it.isMetaPressed || it.isCtrlPressed)
                        if (send) vm.send()
                        send
                    }.takesAttachments(input),
            placeholder = {
                Text(
                    if (state.busy) {
                        "The agent is working: send to steer it or queue for later"
                    } else {
                        "Prompt the agent (Ctrl/Cmd+Enter to send)"
                    },
                    color = c.fgSubtle,
                )
            },
            maxLines = 8,
            framed = false,
        )
        AttachButton(input)
        PrimaryButton(onClick = vm::send, enabled = state.canSend) { Text("↵  Send") }
    }
}

@Composable
private fun RemoveDialog(
    step: RemoveStep?,
    vm: TaskViewModel,
) {
    when (step) {
        null -> {}

        RemoveStep.Confirm -> {
            GlassDialog(
                onDismissRequest = vm::cancelRemove,
                title = { Text("Remove task?") },
                text = { Text("The agent stops and its branch is kept. Delete the worktree as well?") },
                confirmButton = {
                    DangerButton(
                        onClick = { vm.remove(deleteWorktree = true) },
                    ) { Text("Delete worktree") }
                },
                dismissButton = {
                    Row {
                        GhostButton(onClick = vm::cancelRemove) { Text("Cancel") }
                        GhostButton(onClick = { vm.remove(deleteWorktree = false) }) { Text("Keep worktree") }
                    }
                },
            )
        }

        is RemoveStep.Dirty -> {
            GlassDialog(
                onDismissRequest = vm::cancelRemove,
                title = { Text("Uncommitted changes") },
                text = {
                    Notice(
                        "${step.worktree} has changes that are not committed. Deleting it loses them.",
                        Tone.Error,
                    )
                },
                confirmButton = { DangerButton(onClick = vm::forceRemove) { Text("Delete anyway") } },
                dismissButton = { GhostButton(onClick = vm::cancelRemove) { Text("Cancel") } },
            )
        }
    }
}

/** A sunken block that sets tool output and plans apart from the conversation. */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().background(Pyrlyn.colors.bgSunken, CardShape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

private val CardShape = RoundedCornerShape(10.dp)
private const val USER_TINT = 0.55f
