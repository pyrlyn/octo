package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import octo.core.agent.AuthState
import octo.core.agent.PermissionMode
import octo.core.orchestrator.AgentInfo
import octo.ui.theme.Elevation
import octo.ui.theme.GlassBackdrop
import octo.ui.theme.GlassLevel
import octo.ui.theme.Pyrlyn
import octo.ui.theme.glass
import octo.ui.vm.NewTaskDraft
import octo.ui.vm.OnboardingStep
import octo.ui.vm.OnboardingUiState
import octo.ui.vm.OnboardingViewModel

/** The first-run guide, a card over the whole window; [onDone] gets the first task to open. */
@Composable
internal fun OnboardingScreen(
    vm: OnboardingViewModel,
    onDone: (NewTaskDraft?) -> Unit,
) {
    val state by vm.state.collectAsState()
    if (!state.visible) return
    // The guide covers the empty window, so it sits on the bare backdrop and takes every click.
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) {
        },
        contentAlignment = Alignment.Center,
    ) {
        GlassBackdrop(Modifier.matchParentSize())
        Column(
            Modifier
                .width(CardWidth)
                .glass(Pyrlyn.surfaceShape, GlassLevel.Floating, Elevation.E3)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper(state.step)
            when (state.step) {
                OnboardingStep.Welcome -> Welcome(vm)
                OnboardingStep.Repo -> Repo(state, vm)
                OnboardingStep.Agent -> AgentStep(state, vm)
                OnboardingStep.Done -> Done(state, vm, onDone)
            }
            state.error?.let { Notice(it, Tone.Error) }
        }
    }
}

@Composable
private fun Stepper(step: OnboardingStep) {
    val c = Pyrlyn.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        OnboardingStep.entries.forEach {
            val modifier = if (it == step) Modifier.size(24.dp, 6.dp) else Modifier.size(6.dp)
            Box(modifier.background(if (it.ordinal <= step.ordinal) c.accentSolid else c.fgDisabled, CircleShape))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Step ${step.ordinal + 1} of ${OnboardingStep.entries.size}",
            style = Pyrlyn.type.small,
            color = c.fgSubtle,
        )
    }
}

@Composable
private fun Heading(
    title: String,
    body: String,
) {
    Text(title, style = Pyrlyn.type.title.copy(fontSize = HeadingSize), fontWeight = FontWeight.SemiBold)
    Text(body, color = Pyrlyn.colors.fgMuted)
}

/** The step's buttons, at the end of the card. */
@Composable
private fun Actions(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Gap),
        horizontalArrangement = Arrangement.spacedBy(Gap, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Welcome(vm: OnboardingViewModel) {
    Heading(
        "Welcome to Octo",
        "Run several coding agents in parallel, each in its own git worktree, with your own agent subscriptions.",
    )
    Point("⑂", "One worktree per task: agents never step on each other.")
    Point("◎", "Claude Code, Codex, Cursor, Grok and more, through adapters and plugins.")
    Point("◐", "You choose how much each agent may do without asking.")
    Actions {
        GhostButton(onClick = vm::dismiss) { Text("Skip setup") }
        PrimaryButton(onClick = vm::start) { Text("Get started") }
    }
}

@Composable
private fun Point(
    glyph: String,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentTile(glyph, PointTile, Pyrlyn.type.body)
        Text(text)
    }
}

@Composable
private fun Repo(
    state: OnboardingUiState,
    vm: OnboardingViewModel,
) {
    Heading("Pick a repository", "Octo makes a worktree of it for every task. Nothing in the checkout itself changes.")
    GlassField(
        state.repo,
        vm::editRepo,
        singleLine = true,
        label = { Text("Repository") },
        placeholder = { Text("~/src/project") },
        trailingIcon = { GhostButton(onClick = { pickDirectory(state.repo)?.let(vm::editRepo) }) { Text("Browse…") } },
    )
    GlassField(
        state.setup,
        vm::editSetup,
        singleLine = true,
        label = { Text("Setup command (optional)") },
        placeholder = { Text("npm ci") },
        supportingText = { Text("Runs with /bin/sh in every new worktree before the agent starts") },
    )
    Actions {
        GhostButton(onClick = vm::back) { Text("Back") }
        GhostButton(onClick = vm::skipRepo) { Text("Skip") }
        PrimaryButton(onClick = vm::confirmRepo, enabled = !state.checking) {
            Text(if (state.checking) "Checking…" else "Continue")
        }
    }
}

@Composable
private fun AgentStep(
    state: OnboardingUiState,
    vm: OnboardingViewModel,
) {
    Heading("Connect an agent", "Octo uses each agent's own login. It never sees or stores your credentials.")
    val agents = state.agents
    when {
        agents == null -> {
            Text("Looking for agents…", color = Pyrlyn.colors.fgMuted)
        }

        agents.isEmpty() -> {
            Text("No agents are registered in this build.", color = Pyrlyn.colors.fgMuted)
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(SmallGap)) {
                agents.forEach {
                    AgentChoice(
                        it,
                        it.id == state.agent,
                    ) { vm.selectAgent(it.id) }
                }
            }
        }
    }
    state.chosen?.takeIf { state.termsPending }?.let {
        Notice("${it.displayName}'s vendor terms apply before first use; Octo asks for them then.", Tone.Warning)
    }
    Actions {
        GhostButton(onClick = vm::back) { Text("Back") }
        GhostButton(onClick = vm::confirmAgent) { Text("Skip") }
        PrimaryButton(onClick = vm::confirmAgent, enabled = state.agent != null) { Text("Continue") }
    }
}

@Composable
private fun AgentChoice(
    agent: AgentInfo,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val probe = agent.probe
    val found = listOfNotNull("Found", probe.version).joinToString(" ")
    val detail =
        when {
            !probe.installed -> "Not installed"
            probe.auth == AuthState.LoggedOut -> "$found · not signed in: log in with its own CLI"
            probe.auth == AuthState.LoggedIn -> "$found · signed in"
            else -> found
        }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = probe.installed, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = probe.installed)
        Column {
            Text(agent.displayName, color = if (probe.installed) Pyrlyn.colors.fg else Pyrlyn.colors.fgDisabled)
            Text(detail, style = Pyrlyn.type.small, color = Pyrlyn.colors.fgMuted)
        }
    }
}

@Composable
private fun Done(
    state: OnboardingUiState,
    vm: OnboardingViewModel,
    onDone: (NewTaskDraft?) -> Unit,
) {
    Heading("You're set", "One last choice: how much the agent may do without asking.")
    Modes(state.mode, vm::selectMode)
    Text("Change it any time in Settings › Permissions.", style = Pyrlyn.type.small, color = Pyrlyn.colors.fgSubtle)
    Actions {
        GhostButton(onClick = vm::back) { Text("Back") }
        PrimaryButton(onClick = { vm.finish(onDone) }) {
            Text(if (state.repo.isEmpty()) "Finish" else "Start first task")
        }
    }
}

@Composable
private fun Modes(
    mode: PermissionMode,
    onSelect: (PermissionMode) -> Unit,
) {
    OnboardingModes.forEach {
        Row(Modifier.fillMaxWidth().clickable { onSelect(it) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = it == mode, onClick = { onSelect(it) })
            Column {
                Text(it.label, fontWeight = FontWeight.Medium)
                Text(it.description, style = Pyrlyn.type.small, color = Pyrlyn.colors.fgMuted)
            }
        }
    }
}

// Full access is left for Settings: a first task should not run without any prompts by default.
private val OnboardingModes = listOf(PermissionMode.Supervised, PermissionMode.AutoAcceptEdits, PermissionMode.Auto)

private val CardWidth = 640.dp
private val HeadingSize = 20.sp
private val PointTile = 28.dp
