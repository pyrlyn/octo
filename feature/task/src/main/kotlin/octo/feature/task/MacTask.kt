// The task screen's parts as the macOS design draws them; behaviour stays in TaskScreen and TaskViewModel.
package octo.feature.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import octo.core.agent.MODEL_OPTION
import octo.core.agent.PermissionMode
import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.task.Task
import octo.core.task.TranscriptEntry
import octo.design.MonoFont
import octo.feature.common.AttachedFiles
import octo.feature.common.AttachmentInput
import octo.feature.common.PermissionPicker
import octo.feature.common.label
import octo.feature.common.takesAttachments
import octo.feature.common.tone
import pyrlyn.macos.Button
import pyrlyn.macos.ButtonStyle
import pyrlyn.macos.Chip
import pyrlyn.macos.CommandCard
import pyrlyn.macos.Composer
import pyrlyn.macos.Disclosure
import pyrlyn.macos.HoverCard
import pyrlyn.macos.Icon
import pyrlyn.macos.IconButton
import pyrlyn.macos.Label
import pyrlyn.macos.LabeledDivider
import pyrlyn.macos.Mac
import pyrlyn.macos.MacIcon
import pyrlyn.macos.MacSpace
import pyrlyn.macos.Message
import pyrlyn.macos.PickerButton
import pyrlyn.macos.Status
import pyrlyn.macos.Tone
import pyrlyn.macos.bottomHairline

/** The header of the macOS design: title and turn controls, status and branch, then the task's actions. */
@Composable
internal fun MacHeader(
    state: TaskUiState,
    vm: TaskViewModel,
    actions: HeaderActions,
) {
    val task = state.task ?: return
    val c = Mac.colors
    Column(
        Modifier
            .fillMaxWidth()
            .bottomHairline(c.borderHairline)
            .padding(horizontal = MacSpace.xxxl, vertical = MacSpace.xl),
        verticalArrangement = Arrangement.spacedBy(MacSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MacSpace.md)) {
            Label(task.title, Modifier.weight(1f), c.textPrimary, Mac.type.title, maxLines = 1)
            if (state.canCancel) Button("Cancel turn", vm::cancel)
            if (state.canStartNow) Button("Start now", vm::startNow)
            if (state.canRestart) Button("Restart", vm::restart)
            if (state.canStop) Button("Stop", vm::stop, style = ButtonStyle.DestructiveOutline)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MacSpace.md)) {
            Status("${task.agent.value} · ${state.sidebarStatus.label}", state.sidebarStatus.tone)
            Icon(MacIcon.Branch, tint = c.textTertiary)
            Label(
                listOfNotNull(task.branch, task.machine ?: THIS_MAC).joinToString(" · "),
                color = c.textSecondary,
                style = Mac.type.small,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MacSpace.sm)) {
            actions.share?.let { ShareButton(it) }
            Spacer(Modifier.weight(1f))
            // A switch or a move ends the running turn, and a fork taken mid-turn would copy half-written files.
            actions.turn.forEach { Button(it.label, it.run, icon = it.icon, enabled = !state.canCancel) }
            Button("Diff", actions.onDiff, icon = MacIcon.Document)
            // Publishing while the agent writes would push half a change.
            actions.onPublish?.let {
                Button("Publish", it, style = ButtonStyle.Primary, icon = MacIcon.Continue, enabled = !state.canCancel)
            }
            actions.plugins?.let { PluginMenu(it) }
            actions.places?.let { PlaceMenu(task.id, task.place, it) }
            Button("Remove", vm::requestRemove, style = ButtonStyle.Ghost)
        }
    }
}

/** One transcript entry in the macOS design. */
@Composable
internal fun MacEntry(
    entry: TranscriptEntry,
    agent: String,
) {
    val c = Mac.colors
    when (entry) {
        is TranscriptEntry.Text -> {
            val user = entry.role == Role.User
            Message(if (user) "You" else agent, user, Modifier.alpha(if (entry.partial) PARTIAL_ALPHA else 1f)) {
                // A message can be files alone, and an empty line above them would look like a lost text.
                if (entry.text.isNotEmpty()) Label(entry.text)
                AttachedFiles(entry.attachments)
            }
        }

        is TranscriptEntry.Thought -> {
            Expandable("Thinking", entry.text)
        }

        is TranscriptEntry.Tool -> {
            CommandCard(
                entry.title,
                entry.status.label,
                entry.status.tone,
                output = entry.detail?.takeIf { it.isNotBlank() },
            )
        }

        is TranscriptEntry.Plan -> {
            MacPlan(entry)
        }

        is TranscriptEntry.Request -> {
            Column {
                Label(
                    "Asked: ${entry.title}" + if (entry.resolved) " (answered)" else "",
                    color = c.textSecondary,
                    style = Mac.type.small,
                )
                AttachedFiles(entry.attachments)
            }
        }

        is TranscriptEntry.Notice -> {
            Label(entry.text, color = if (entry.error) c.statusError else c.textSecondary, style = Mac.type.small)
        }

        is TranscriptEntry.Checkpoint -> {
            LabeledDivider("Checkpoint ${entry.id}")
        }
    }
}

/** A checkpoint the worktree can go back to, as a labelled rule with its revert link. */
@Composable
internal fun MacCheckpoint(
    entry: TranscriptEntry.Checkpoint,
    revertable: Boolean,
    enabled: Boolean,
    onRevert: (TranscriptEntry.Checkpoint) -> Unit,
) {
    // Reverting under a running turn would fight the agent for the files, so the link waits for the turn.
    val action = "Revert to here".takeIf { revertable && enabled }
    LabeledDivider("Checkpoint ${entry.id}", action = action, onAction = { onRevert(entry) })
}

/**
 * The composer of the macOS design: attach and the permission mode at the leading edge, the agent and its model
 * before send. Cmd or Ctrl+Enter sends, files drop or paste in.
 */
@Composable
internal fun MacPromptBox(
    state: TaskUiState,
    vm: TaskViewModel,
    /** Null while the `handoff` flag is off; the agent then shows without a way to change it. */
    onSwitchAgent: (() -> Unit)?,
) {
    val task = state.task ?: return
    val input = remember(vm) { AttachmentInput(vm::attach, vm::showError) }
    Column(verticalArrangement = Arrangement.spacedBy(MacSpace.sm)) {
        MacComposer(state, vm, task, input, onSwitchAgent)
        listOfNotNull(state.context?.let { "Context ${it.label}" }, state.tokens?.line)
            .takeIf { it.isNotEmpty() }
            ?.let {
                Label(
                    it.joinToString("  ·  "),
                    Modifier.padding(horizontal = MacSpace.lg),
                    color = Mac.colors.textTertiary,
                    style = Mac.type.caption.copy(fontFamily = MonoFont),
                )
            }
    }
}

@Composable
private fun MacComposer(
    state: TaskUiState,
    vm: TaskViewModel,
    task: Task,
    input: AttachmentInput,
    onSwitchAgent: (() -> Unit)?,
) {
    Composer(
        value = state.draft,
        onValueChange = vm::editDraft,
        onSend = vm::send,
        modifier =
            Modifier
                .onPreviewKeyEvent {
                    val send =
                        it.type == KeyEventType.KeyDown && it.key == Key.Enter && (it.isMetaPressed || it.isCtrlPressed)
                    if (send) vm.send()
                    send
                }.takesAttachments(input),
        placeholder = if (state.busy) BUSY_PLACEHOLDER else "Message ${task.agent.value}…",
        hint = "⌘↵",
        sendEnabled = state.canSend,
        trailing = {
            if (state.context != null || state.tokens != null) {
                HoverCard(MacIcon.Gauge, "Context and tokens") { SessionDetails(state.context, state.tokens) }
            }
            // A switch hands the task to another agent, which ends the running turn first.
            PickerButton(
                task.agent.value,
                onSwitchAgent ?: {},
                detail = task.options[MODEL_OPTION],
                icon = MacIcon.Sparkle,
                enabled = onSwitchAgent != null && !state.canCancel,
            )
        },
    ) {
        IconButton(MacIcon.Plus, "Attach files", input::choose)
        PermissionPicker(task.permissionMode, PermissionMode.entries, { it?.let(vm::setPermissionMode) })
        state.attachments.forEachIndexed { index, file ->
            Chip(file.name, Modifier.clickable { vm.detach(index) }, tone = Tone.Idle)
        }
    }
}

@Composable
private fun MacPlan(entry: TranscriptEntry.Plan) {
    val c = Mac.colors
    Column(verticalArrangement = Arrangement.spacedBy(MacSpace.xs)) {
        Label("Plan", color = c.textSecondary, style = Mac.type.captionBold)
        entry.entries.forEach {
            Label((if (it.done) "✓  " else "○  ") + it.text, color = if (it.done) c.textSecondary else c.textPrimary)
        }
    }
}

@Composable
private fun Expandable(
    label: String,
    body: String,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(MacSpace.xs)) {
        Disclosure(label, onClick = { open = !open })
        if (open) Label(body, Modifier.padding(start = MacSpace.xxxl), Mac.colors.textSecondary, Mac.type.small)
    }
}

private val ToolStatus.tone: Tone
    get() =
        when (this) {
            ToolStatus.Pending -> Tone.Idle
            ToolStatus.InProgress -> Tone.Busy
            ToolStatus.Completed -> Tone.Ok
            ToolStatus.Failed -> Tone.Error
        }

private const val THIS_MAC = "This Mac"
private const val BUSY_PLACEHOLDER = "The agent is working: send to steer it or queue for later"
private const val PARTIAL_ALPHA = 0.6f
