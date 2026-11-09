package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import octo.design.Text
import octo.feature.common.label
import octo.feature.common.tone
import octo.ui.vm.MainUiState
import octo.ui.vm.MainViewModel
import pyrlyn.macos.Button
import pyrlyn.macos.ButtonStyle
import pyrlyn.macos.IconTile
import pyrlyn.macos.Label
import pyrlyn.macos.Mac
import pyrlyn.macos.MacIcon
import pyrlyn.macos.MacSpace
import pyrlyn.macos.SectionLabel
import pyrlyn.macos.SettingsItem
import pyrlyn.macos.SidebarRow
import pyrlyn.macos.topHairline

/**
 * The sidebar of the macOS design: the mark and New task, the tasks by repository, and the footer links.
 * Running agents are listed in the Agents pane the footer opens, so the sidebar does not repeat them.
 */
@Composable
internal fun MacSidebar(
    state: MainUiState,
    vm: MainViewModel,
    modifier: Modifier,
    onDevices: (() -> Unit)?,
    onUsage: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    top: @Composable () -> Unit,
) {
    Column(
        modifier.background(Mac.colors.bgChrome).padding(start = MacSpace.xl, end = MacSpace.xl, bottom = MacSpace.xl),
        verticalArrangement = Arrangement.spacedBy(MacSpace.xs),
    ) {
        top()
        Row(
            Modifier.fillMaxWidth().padding(
                start = MacSpace.xs,
                end = MacSpace.xs,
                top = MacSpace.md,
                bottom = MacSpace.xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(MacIcon.Terminal)
            Label("Octo", color = Mac.colors.textPrimary, style = Mac.type.title)
            Spacer(Modifier.weight(1f))
            Button("New task", vm::openNewTask, style = ButtonStyle.Primary, icon = MacIcon.Plus)
        }
        MacTaskList(state, vm, Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().topHairline(Mac.colors.borderHairline).padding(top = MacSpace.md),
            verticalArrangement = Arrangement.spacedBy(MacSpace.xxs),
        ) {
            SettingsItem(MacIcon.Sparkle, "Agents", vm::openAgents, selected = state.agentsOpen)
            onUsage?.let { SettingsItem(MacIcon.Eye, "Usage", it, selected = state.usageOpen) }
            onDevices?.let { SettingsItem(MacIcon.Server, "Devices", it, selected = state.devicesOpen) }
            onSettings?.let { SettingsItem(MacIcon.Gear, "Settings", it, selected = state.settingsOpen) }
        }
    }
}

/** The tasks, one section per repository; forks sit indented under their parent. */
@Composable
private fun MacTaskList(
    state: MainUiState,
    vm: MainViewModel,
    modifier: Modifier,
) {
    val now = rememberNowMs()
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(MacSpace.xxs)) {
        state.groups.forEach { group ->
            item(key = "repo:${group.repo}") { SectionLabel(group.name) }
            items(group.tasks, key = { it.id.value }) { row ->
                Column(Modifier.padding(start = ForkIndent * row.depth.coerceAtMost(MAX_FORK_INDENT))) {
                    row.sectionTitle?.let { SectionLabel(it) }
                    row.groupTitle?.let {
                        Text(
                            it,
                            Modifier.padding(start = MacSpace.lg, top = MacSpace.xs),
                            style = Mac.type.caption,
                            color = Mac.colors.textGhost,
                            maxLines = 1,
                        )
                    }
                    SidebarRow(
                        title = row.title,
                        subtitle = "${row.agent.value} · ${if (row.limited) "Limited" else row.status.label}",
                        tone = row.status.tone,
                        selected = row.id == state.selected,
                        onClick = { vm.select(row.id) },
                        trailing =
                            row.age(now)?.let {
                                { Label(it, color = Mac.colors.textSecondary, style = Mac.type.caption, maxLines = 1) }
                            },
                    )
                }
            }
        }
    }
}

private const val MAX_FORK_INDENT = 3
private val ForkIndent = MacSpace.xxxl
