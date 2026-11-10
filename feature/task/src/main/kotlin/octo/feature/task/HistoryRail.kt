package octo.feature.task

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import octo.core.agent.Role
import octo.core.task.TranscriptEntry
import octo.feature.common.compactAge
import octo.feature.common.label
import pyrlyn.macos.Label
import pyrlyn.macos.Mac
import pyrlyn.macos.MacRadius
import pyrlyn.macos.MacSpace
import pyrlyn.macos.elevatedSurface
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** One of the user's turns: where its prompt sits in the transcript, the prompt, and the start of the reply. */
internal data class HistoryTurn(
    val index: Int,
    val message: String,
    val reply: String?,
    val atMs: Long?,
)

/** How a tick stands out: the turn in view (or under the pointer) is [Current], its neighbours [Near]. */
internal enum class HistoryTick { Default, Near, Current }

/** A tick for every prompt; the reply is the agent's first message before the next prompt. */
internal fun historyTurns(entries: List<TranscriptEntry>): List<HistoryTurn> {
    val turns = mutableListOf<HistoryTurn>()
    entries.forEachIndexed { index, entry ->
        if (entry !is TranscriptEntry.Text) return@forEachIndexed
        if (entry.role == Role.User) {
            turns += HistoryTurn(index, preview(entry), null, entry.atMs)
        } else if (turns.isNotEmpty() && turns.last().reply == null) {
            turns[turns.lastIndex] = turns.last().copy(reply = preview(entry))
        }
    }
    return turns
}

/** The rail only helps once there is an earlier turn to go back to. */
internal fun showsRail(turns: List<HistoryTurn>): Boolean = turns.size > 1

/** The turn the top of the view is in; above the first prompt, that first one. */
internal fun currentTurn(
    turns: List<HistoryTurn>,
    firstVisible: Int,
): Int = turns.indexOfLast { it.index <= firstVisible }.coerceAtLeast(0)

internal fun tick(
    position: Int,
    current: Int,
): HistoryTick =
    when (abs(position - current)) {
        0 -> HistoryTick.Current
        1 -> HistoryTick.Near
        else -> HistoryTick.Default
    }

/** The card's first line, "You · 1h ago"; a prompt kept before send times were recorded has no age. */
internal fun turnHeading(
    atMs: Long?,
    nowMs: Long,
): String {
    val age = atMs?.let { (nowMs - it).milliseconds } ?: return "You"
    return if (age < 1.minutes) "You · now" else "You · ${compactAge(age)} ago"
}

/** The message on one line, capped, so the card's line limit cuts a short string; files alone are named. */
internal fun preview(entry: TranscriptEntry.Text): String {
    val line = entry.text.trim().replace(Whitespace, " ")
    val text = if (line.length > PREVIEW_CHARS) line.take(PREVIEW_CHARS).trimEnd() + "…" else line
    return text.ifEmpty { entry.attachments.label() }
}

/**
 * A short tick per user turn in the transcript's left gutter. Hovering one shows the turn in a card; a click scrolls
 * [list] to its prompt.
 */
@Composable
internal fun HistoryRail(
    entries: List<TranscriptEntry>,
    list: LazyListState,
    modifier: Modifier = Modifier,
) {
    val turns = remember(entries) { historyTurns(entries) }
    if (!showsRail(turns)) return
    val scope = rememberCoroutineScope()
    val inView by remember(list, turns) { derivedStateOf { currentTurn(turns, list.firstVisibleItemIndex) } }
    var hovered by remember { mutableStateOf<Int?>(null) }
    val current = hovered ?: inView
    Column(modifier.verticalScroll(rememberScrollState())) {
        turns.forEachIndexed { position, turn ->
            TickRow(
                turn,
                tick(position, current),
                onHover = { over -> hovered = if (over) position else hovered.takeUnless { it == position } },
                onClick = { scope.launch { list.animateScrollToItem(turn.index) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TickRow(
    turn: HistoryTurn,
    state: HistoryTick,
    onHover: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val over by hover.collectIsHoveredAsState()
    LaunchedEffect(over) { onHover(over) }
    val c = Mac.colors
    TooltipArea(
        tooltip = { Preview(turn) },
        delayMillis = PREVIEW_DELAY_MS,
        tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.CenterEnd, Alignment.BottomEnd, PreviewOffset),
    ) {
        Box(
            Modifier
                .size(RailWidth, TickRow)
                .hoverable(hover)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            val (width, color) =
                when (state) {
                    HistoryTick.Current -> RailWidth to c.textPrimary
                    HistoryTick.Near -> NearWidth to c.textTertiary
                    HistoryTick.Default -> DefaultWidth to c.textGhost
                }
            Box(Modifier.size(width, TickThickness).background(color, TickShape))
        }
    }
}

@Composable
private fun Preview(turn: HistoryTurn) {
    val c = Mac.colors
    val type = Mac.type
    val nowMs = remember { Clock.System.now().toEpochMilliseconds() }
    Column(
        Modifier
            .width(PreviewWidth)
            .elevatedSurface(RoundedCornerShape(MacRadius.md))
            .padding(horizontal = MacSpace.xl, vertical = MacSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MacSpace.xs),
    ) {
        Label(turnHeading(turn.atMs, nowMs), color = c.textTertiary, style = type.caption, maxLines = 1)
        Label(turn.message, color = c.textPrimary, style = type.body, maxLines = MESSAGE_LINES)
        turn.reply?.let { Label(it, color = c.textSecondary, style = type.small, maxLines = REPLY_LINES) }
    }
}

private val Whitespace = Regex("\\s+")
private const val PREVIEW_CHARS = 280
private const val MESSAGE_LINES = 3
private const val REPLY_LINES = 2
private const val PREVIEW_DELAY_MS = 150

// The History tick widths from the design: Default, Near and Current, which is as wide as the rail.
private val DefaultWidth = MacSpace.md
private val NearWidth = MacSpace.xl
private val RailWidth = MacSpace.xxxl
private val TickThickness = MacSpace.xxs

// The 2pt tick and the 8pt gap of the design, as one row the pointer can hit.
private val TickRow = MacSpace.lg
private val TickShape = RoundedCornerShape(50)
private val PreviewWidth = 300.dp

// The card opens a gap right of the rail and a little above the tick, as on the design's hover screen.
private val PreviewOffset = DpOffset(MacSpace.xl, -(MacSpace.xxxl + MacSpace.md))
