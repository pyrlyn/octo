package octo.ui

import androidx.compose.foundation.layout.Arrangement
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
import octo.core.agent.Role
import octo.core.status.AgentStatus
import octo.core.task.TranscriptEntry
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
) {
    val state by vm.state.collectAsState()
    val task = state.task ?: return
    Column(Modifier.fillMaxSize().padding(LargeGap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        // Publishing runs this computer's git and gh, which cannot reach a remote worktree.
        Header(state, vm, onDiff, onPublish.takeIf { task.machine == null }, plugins)
        Text(
            task.machine?.let { "${task.branch} · on $it" } ?: task.branch,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
        HorizontalDivider()
        Transcript(state.transcript, Modifier.weight(1f).fillMaxWidth())
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
        OutlinedButton(onClick = onDiff) { Text("Diff") }
        // Publishing while the agent writes would push half a change.
        onPublish?.let { OutlinedButton(onClick = it, enabled = !state.canCancel) { Text("Publish") } }
        plugins?.let { PluginMenu(it) }
        TextButton(onClick = vm::requestRemove) { Text("Remove") }
    }
}

@Composable
private fun Transcript(
    entries: List<TranscriptEntry>,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    // Follow the live output like a terminal does.
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) list.scrollToItem(entries.lastIndex) }
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
            Text(entry.text, color = muted, fontStyle = FontStyle.Italic)
        }

        is TranscriptEntry.Tool -> {
            Text(
                "Tool · ${entry.title} · ${entry.status.name}" + entry.detail?.let { "\n$it" }.orEmpty(),
                color = muted,
            )
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

@Composable
private fun PromptBox(
    state: TaskUiState,
    vm: TaskViewModel,
) {
    val idle = state.status == AgentStatus.Idle
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
            placeholder = { Text(if (idle) "Prompt the agent (Ctrl/Cmd+Enter to send)" else "The agent is busy") },
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
