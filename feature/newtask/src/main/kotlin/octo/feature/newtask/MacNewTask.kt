// The New task form as the macOS design draws it: pickers for where the task runs, then a composer for what it does.
package octo.feature.newtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import octo.design.GlassField
import octo.design.Notice
import octo.design.Text
import octo.feature.common.AgentPicker
import octo.feature.common.AttachmentInput
import octo.feature.common.PermissionPicker
import octo.feature.common.pickDirectory
import octo.feature.common.takesAttachments
import pyrlyn.macos.Chip
import pyrlyn.macos.Composer
import pyrlyn.macos.IconButton
import pyrlyn.macos.Label
import pyrlyn.macos.Mac
import pyrlyn.macos.MacIcon
import pyrlyn.macos.MacSpace
import pyrlyn.macos.Menu
import pyrlyn.macos.MenuItem
import pyrlyn.macos.MenuSection
import pyrlyn.macos.PickerButton
import pyrlyn.macos.SearchField
import octo.design.Tone as DesignTone
import pyrlyn.macos.Tone as MacTone

@Composable
internal fun MacForm(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
    onEditPresets: () -> Unit,
) {
    Column(
        // The scroll clips to its bounds; the room inside it keeps the composer's shadow whole.
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = MacSpace.xxxxl),
        verticalArrangement = Arrangement.spacedBy(MacSpace.xl),
    ) {
        // A remote machine's disk is out of the folder chooser's reach, so its path is typed.
        state.machine?.let {
            GlassField(state.repo, vm::editRepo, Modifier.fillMaxWidth(), label = {
                Text("Repository path on $it")
            }, singleLine = true)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MacSpace.xxs),
        ) {
            if (state.machine == null) {
                PickerButton(
                    state.repo
                        .trimEnd('/')
                        .substringAfterLast('/')
                        .ifEmpty { "Choose a repository" },
                    { pickDirectory(state.repo)?.let(vm::editRepo) },
                    icon = MacIcon.Folder,
                )
            }
            BranchPicker(state, vm)
            if (state.machines.isNotEmpty()) MachinePicker(state, vm)
        }
        branchNote(state, noMatch = false)?.let { (text, error) ->
            Label(text, color = if (error) Mac.colors.statusError else Mac.colors.textSecondary, style = Mac.type.small)
        }
        if (state.presetsEnabled || state.compareEnabled) ModeChoice(state, vm)
        when {
            state.team -> TeamChoice(state, vm, onEditPresets)
            state.compare -> CompareChoice(state, vm)
            else -> MacPrompt(state, vm)
        }
        state.error?.let { Notice(it, DesignTone.Error) }
    }
}

/** The title, then the prompt as a composer whose pickers set the permission mode and the agent. */
@Composable
private fun MacPrompt(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    GlassField(
        state.title,
        vm::editTitle,
        Modifier.fillMaxWidth(),
        placeholder = { Text(state.effectiveTitle.ifEmpty { "Title, taken from the prompt when empty" }) },
        singleLine = true,
    )
    val input = remember(vm) { AttachmentInput(vm::attach, vm::showError) }
    Composer(
        value = state.prompt,
        onValueChange = vm::editPrompt,
        onSend = vm::create,
        modifier = Modifier.takesAttachments(input),
        placeholder = "Describe the task for the agent…",
        sendLabel = "Start task",
        sendEnabled = state.canCreate,
        minHeight = PROMPT_HEIGHT,
        trailing = {
            AgentPicker(
                state.agents,
                state.selectedAgent,
                { vm.selectAgent(it.id) },
                if (state.loadingAgents) "Looking for agents…" else "No agent installed",
            )
        },
    ) {
        IconButton(MacIcon.Plus, "Attach files", input::choose)
        if (state.permissionModes.size > 1) {
            PermissionPicker(
                state.chosenPermissionMode,
                state.permissionModes,
                vm::selectPermissionMode,
                "Default permissions",
            )
        }
        state.attachments.forEachIndexed { index, file ->
            Chip(file.name, Modifier.clickable { vm.detach(index) }, tone = MacTone.Idle)
        }
    }
}

/** The base branch, searched in its menu; only a listed branch is taken. */
@Composable
private fun BranchPicker(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val matches = state.branches.filter { it.contains(query.trim(), ignoreCase = true) }
    val pick = { branch: String ->
        vm.selectBaseRef(branch)
        open = false
        query = ""
    }
    Box {
        PickerButton(state.baseRef, { open = true }, icon = MacIcon.Branch, enabled = state.branches.isNotEmpty())
        Menu(open, {
            open = false
            query = ""
        }) {
            SearchField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(bottom = MacSpace.xs),
                placeholder = "Search branches",
                keyboardActions = KeyboardActions(onDone = { matches.firstOrNull()?.let(pick) }),
            )
            // A long branch list would run off the window; typing narrows it instead of scrolling.
            matches.take(MAX_BRANCHES).forEach { MenuItem(it, { pick(it) }, checked = it == state.baseRef) }
            when {
                matches.isEmpty() -> MenuSection("No branch matches")
                matches.size > MAX_BRANCHES -> MenuSection("${matches.size - MAX_BRANCHES} more: type to narrow")
            }
        }
    }
}

@Composable
private fun MachinePicker(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    var open by remember { mutableStateOf(false) }
    val pick = { id: String? ->
        open = false
        vm.selectMachine(id)
    }
    Box {
        PickerButton(state.machine ?: THIS_COMPUTER, { open = true }, icon = MacIcon.Server)
        Menu(open, { open = false }) {
            MenuSection("Local")
            MenuItem(THIS_COMPUTER, { pick(null) }, checked = state.machine == null, icon = MacIcon.Terminal)
            MenuSection("Remote")
            state.machines.forEach { MenuItem(it, { pick(it) }, checked = it == state.machine, icon = MacIcon.Server) }
        }
    }
}

private const val THIS_COMPUTER = "This computer"
private const val MAX_BRANCHES = 12
private val PROMPT_HEIGHT = 72.dp
