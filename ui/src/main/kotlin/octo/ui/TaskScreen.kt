package octo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import octo.core.agent.PermissionMode
import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.status.AgentStatus
import octo.core.task.TranscriptEntry
import octo.ui.vm.MainViewModel
import octo.ui.vm.PluginActionsViewModel
import octo.ui.vm.RemoveStep
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
    /** Null while the `sections` flag is off, which hides pinning, snoozing and settling. */
    places: MainViewModel? = null,
) {
    val state by vm.state.collectAsState()
    val task = state.task ?: return
    Column(Modifier.fillMaxSize().padding(LargeGap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        // Publishing runs this computer's git and gh, which cannot reach a remote worktree.
        Header(state, vm, onDiff, onPublish.takeIf { task.machine == null }, plugins, onSwitchAgent, onFork, places)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Text(
                task.machine?.let { "${task.branch} · on $it" } ?: task.branch,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionMenu(task.permissionMode, vm::setPermissionMode)
        }
        state.failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.limit?.let { LimitBanner(it, onResume = vm::resume) }
        state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
        HorizontalDivider()
        Transcript(state.transcript, Modifier.weight(1f).fillMaxWidth())
        Queue(state, vm)
        state.pending.forEach { RequestPanel(it, state.answers[it.id].orEmpty(), vm) }
        PromptBox(state, vm)
    }
    RemoveDialog(state.remove, vm)
}

@Composable
private fun Header(
    state: TaskUiState,
    vm: TaskViewModel,
    onDiff: () -> Unit,
    onPublish: (() -> Unit)?,
    plugins: PluginActionsViewModel?,
    onSwitchAgent: (() -> Unit)?,
    onFork: (() -> Unit)?,
    places: MainViewModel?,
) {
    val task = state.task ?: return
    val status = state.sidebarStatus
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        StatusDot(status)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            Text("${task.agent.value} · ${status.label}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.canCancel) OutlinedButton(onClick = vm::cancel) { Text("Cancel turn") }
        if (state.canStop) OutlinedButton(onClick = vm::stop) { Text("Stop") }
        if (state.canRestart) OutlinedButton(onClick = vm::restart) { Text("Restart") }
        // A switch ends the running turn, so it waits until the agent is done.
        onSwitchAgent?.let { OutlinedButton(onClick = it, enabled = !state.canCancel) { Text("Switch agent") } }
        // A fork taken mid-turn would copy half-written files.
        onFork?.let { OutlinedButton(onClick = it, enabled = !state.canCancel) { Text("Fork") } }
        OutlinedButton(onClick = onDiff) { Text("Diff") }
        // Publishing while the agent writes would push half a change.
        onPublish?.let { OutlinedButton(onClick = it, enabled = !state.canCancel) { Text("Publish") } }
        plugins?.let { PluginMenu(it) }
        places?.let { PlaceMenu(task.id, task.place, it) }
        TextButton(onClick = vm::requestRemove) { Text("Remove") }
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
        TextButton(onClick = { open = true }) { Text("Permissions: ${current.label}") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            PermissionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
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
    entries: List<TranscriptEntry>,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    // Follow the live output like a terminal does; tool updates and streamed text change the last
    // entry in place, so its content is a key too.
    LaunchedEffect(entries.size, entries.lastOrNull()) {
        if (entries.isNotEmpty()) list.scrollToItem(entries.lastIndex)
    }
    SelectionContainer(modifier) {
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(Gap)) {
            itemsIndexed(entries) { _, entry -> Entry(entry) }
        }
    }
}

@Composable
private fun Entry(entry: TranscriptEntry) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (entry) {
        is TranscriptEntry.Text -> {
            Column(Modifier.alpha(if (entry.partial) PARTIAL_ALPHA else 1f)) {
                Text(if (entry.role == Role.User) "You" else "Agent", fontWeight = FontWeight.SemiBold)
                Text(entry.text)
            }
        }

        is TranscriptEntry.Thought -> {
            Collapsible("Thinking", entry.text, FontStyle.Italic)
        }

        is TranscriptEntry.Tool -> {
            Collapsible("${entry.title} · ${entry.status.label}", entry.detail)
        }

        is TranscriptEntry.Plan -> {
            Column {
                Text("Plan", fontWeight = FontWeight.SemiBold)
                entry.entries.forEach { Text((if (it.done) "[x] " else "[ ] ") + it.text) }
            }
        }

        is TranscriptEntry.Request -> {
            Text("Asked: ${entry.title}" + if (entry.resolved) " (answered)" else "", color = muted)
        }

        is TranscriptEntry.Notice -> {
            Text(entry.text, color = if (entry.error) MaterialTheme.colorScheme.error else muted)
        }
    }
}

/** Prompts waiting for the agent, each editable in place until it is sent. */
@Composable
private fun Queue(
    state: TaskUiState,
    vm: TaskViewModel,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    state.queue.forEach { queued ->
        val editing = state.editing?.takeIf { it.id == queued.id }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Text("Queued", color = muted, fontWeight = FontWeight.SemiBold)
            if (editing == null) {
                Text(queued.text, Modifier.weight(1f), color = muted, maxLines = 2)
                TextButton(onClick = { vm.editQueued(queued) }) { Text("Edit") }
            } else {
                OutlinedTextField(editing.text, vm::editQueuedText, Modifier.weight(1f), maxLines = 4)
                TextButton(onClick = vm::saveQueued, enabled = editing.text.isNotBlank()) { Text("Save") }
                TextButton(onClick = vm::cancelQueuedEdit) { Text("Cancel") }
            }
            TextButton(onClick = { vm.removeQueued(queued.id) }) { Text("Remove") }
        }
    }
}

/** A one-line header; thoughts and tool output run to pages, so the body opens on a click. */
@Composable
private fun Collapsible(
    header: String,
    body: String?,
    bodyStyle: FontStyle = FontStyle.Normal,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var open by remember { mutableStateOf(false) }
    val expandable = !body.isNullOrBlank()
    val marker =
        when {
            !expandable -> "  "
            open -> "▾ "
            else -> "▸ "
        }
    Column {
        Text(
            marker + header,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (expandable) Modifier.clickable { open = !open } else Modifier,
        )
        if (open && body != null) {
            Text(body, color = muted, fontStyle = bodyStyle, modifier = Modifier.padding(start = LargeGap))
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
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = vm::editDraft,
            modifier =
                Modifier.weight(1f).onPreviewKeyEvent {
                    val send =
                        it.type == KeyEventType.KeyDown && it.key == Key.Enter && (it.isMetaPressed || it.isCtrlPressed)
                    if (send) vm.send()
                    send
                },
            placeholder = {
                Text(
                    if (state.busy) {
                        "The agent is working: send to steer it or queue for later"
                    } else {
                        "Prompt the agent (Ctrl/Cmd+Enter to send)"
                    },
                )
            },
            minLines = 2,
            maxLines = 8,
        )
        Button(onClick = vm::send, enabled = state.canSend) { Text("Send") }
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
            AlertDialog(
                onDismissRequest = vm::cancelRemove,
                title = { Text("Remove task?") },
                text = { Text("The agent stops and its branch is kept. Delete the worktree as well?") },
                confirmButton = { Button(onClick = { vm.remove(deleteWorktree = true) }) { Text("Delete worktree") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = vm::cancelRemove) { Text("Cancel") }
                        TextButton(onClick = { vm.remove(deleteWorktree = false) }) { Text("Keep worktree") }
                    }
                },
            )
        }

        is RemoveStep.Dirty -> {
            AlertDialog(
                onDismissRequest = vm::cancelRemove,
                title = { Text("Uncommitted changes") },
                text = { Text("${step.worktree} has changes that are not committed. Deleting it loses them.") },
                confirmButton = { Button(onClick = vm::forceRemove) { Text("Delete anyway") } },
                dismissButton = { TextButton(onClick = vm::cancelRemove) { Text("Cancel") } },
            )
        }
    }
}
