package octo.feature.common

import androidx.compose.runtime.Composable
import octo.core.agent.AuthState
import octo.core.orchestrator.AgentInfo
import octo.design.Choice
import octo.design.Pyrlyn

@Composable
fun AgentChoice(
    agent: AgentInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    /** A checkbox, for picking several agents, instead of a radio button. */
    check: Boolean = false,
) {
    Choice(
        agent.displayName,
        selected,
        onSelect,
        note = agentNote(agent),
        enabled = agent.probe.installed,
        multiple = check,
    )
}

/** The version, or why the agent cannot run yet. */
fun agentNote(agent: AgentInfo): String {
    val probe = agent.probe
    return when {
        !probe.installed -> "not installed"
        probe.auth == AuthState.LoggedOut -> "${probe.version.orEmpty()} · log in with its own CLI first"
        probe.versionDrift -> "${probe.version} · tested with ${probe.verifiedVersion}"
        else -> probe.version.orEmpty()
    }
}

@Composable
fun authColor(auth: AuthState?) =
    when (auth) {
        AuthState.LoggedIn -> Pyrlyn.colors.success
        AuthState.LoggedOut -> Pyrlyn.colors.warning
        AuthState.Unknown, null -> Pyrlyn.colors.fgSubtle
    }
