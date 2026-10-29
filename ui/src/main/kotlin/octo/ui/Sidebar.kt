package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.design.AccentTile
import octo.design.Gap
import octo.design.GhostButton
import octo.design.Glyph
import octo.design.LargeGap
import octo.design.Meter
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.SectionLabel
import octo.design.SmallGap
import octo.design.Text
import octo.feature.agents.AgentRow
import octo.feature.agents.contextShare
import octo.feature.common.StatusDot
import octo.feature.common.label
import octo.ui.vm.MainUiState
import octo.ui.vm.MainViewModel
import octo.ui.vm.TaskRow

@Composable
internal fun Sidebar(
    state: MainUiState,
    vm: MainViewModel,
    /** The running agents; picking one opens its task. */
    agents: List<AgentRow>,
    modifier: Modifier = Modifier,
    /** Null hides the action, while remote access is off. */
    onDevices: (() -> Unit)? = null,
    /** Null hides the action, while the `usage` flag is off. */
    onUsage: (() -> Unit)? = null,
    /** Null hides the action, where there is no `config.json` to edit, as in tests. */
    onSettings: (() -> Unit)? = null,
) {
    Column(modifier.padding(horizontal = Gap)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = Gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            OctoMark()
            Text("Octo", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            PrimaryButton(onClick = vm::openNewTask) { Text("+  New task") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.groups.forEach { group ->
                item(key = "repo:${group.repo}") {
                    Row(
                        Modifier.padding(start = 10.dp, end = Gap, top = 10.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.status?.let { StatusDot(it) }
                        Text(
                            group.name,
                            style = Pyrlyn.type.label,
                            color = Pyrlyn.colors.fgMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                items(group.tasks, key = { it.id.value }) { row ->
                    Column {
                        row.sectionTitle?.let { SectionLabel(it, Modifier.padding(start = 10.dp, top = Gap)) }
                        row.groupTitle?.let {
                            Text(
                                it,
                                Modifier.padding(start = LargeGap, end = Gap, top = SmallGap),
                                style = Pyrlyn.type.small,
                                color = Pyrlyn.colors.fgSubtle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TaskRowItem(row, selected = row.id == state.selected, onClick = { vm.select(row.id) })
                    }
                }
            }
        }
        if (agents.isNotEmpty()) {
            SectionLabel("Agents", Modifier.padding(start = 10.dp, top = Gap, bottom = SmallGap))
            agents.forEach { AgentItem(it, onClick = { vm.select(it.id) }) }
        }
        Footer(vm, onDevices, onUsage, onSettings)
    }
}

@Composable
private fun Footer(
    vm: MainViewModel,
    onDevices: (() -> Unit)?,
    onUsage: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Gap), horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
        GhostButton(onClick = vm::openAgents) { Text("Agents") }
        onUsage?.let { GhostButton(onClick = it) { Text("Usage") } }
        onDevices?.let { GhostButton(onClick = it) { Text("Devices") } }
        Spacer(Modifier.weight(1f))
        onSettings?.let { GhostButton(onClick = it) { Text("⚙") } }
    }
}

@Composable
private fun AgentItem(
    row: AgentRow,
    onClick: () -> Unit,
) {
    val c = Pyrlyn.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(minOf(Pyrlyn.platform.radiusControl, RowRadius)))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = SmallGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Glyph(row.icon?.svg, row.agent, AgentIconSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.title, style = Pyrlyn.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.contextShare?.let { Meter(it, Modifier.fillMaxWidth()) }
        }
        Text(
            "${row.cpu} · ${row.memory}",
            style = Pyrlyn.type.mono.copy(fontSize = Pyrlyn.type.small.fontSize),
            color = c.fgSubtle,
            maxLines = 1,
        )
    }
}

@Composable
private fun TaskRowItem(
    row: TaskRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Pyrlyn.colors
    val shape = RoundedCornerShape(minOf(Pyrlyn.platform.radiusControl, RowRadius))
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val background =
        when {
            selected -> c.stateSelected
            hovered -> c.stateHover
            else -> Color.Transparent
        }
    Row(
        Modifier
            .padding(start = LargeGap * row.depth.coerceAtMost(MAX_FORK_INDENT))
            .fillMaxWidth()
            .heightIn(min = Pyrlyn.platform.rowHeight + RowExtra)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(0.5.dp, c.stateSelectedRim, shape) else Modifier)
            .hoverable(hover)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(row.status)
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                color = if (selected) c.stateSelectedFg else c.fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${row.agent.value} · ${if (row.limited) "Limited" else row.status.label}",
                style = Pyrlyn.type.small,
                color = c.fgMuted,
                maxLines = 1,
            )
        }
    }
}

/** The app mark: a prompt on an accent tile. */
@Composable
private fun OctoMark() {
    AccentTile("❯_", MarkSize, Pyrlyn.type.mono.copy(fontSize = Pyrlyn.type.small.fontSize * MARK_TEXT))
}

/** Deeper forks stop moving right, so a long chain still leaves room for the title. */
private const val MAX_FORK_INDENT = 3
private val RowRadius = 10.dp
private val RowExtra = 14.dp
private val MarkSize = 22.dp
private val AgentIconSize = 18.dp
private const val MARK_TEXT = 0.85f
