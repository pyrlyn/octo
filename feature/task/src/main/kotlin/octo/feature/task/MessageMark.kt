package octo.feature.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import octo.core.agent.Role
import octo.core.task.TranscriptEntry
import octo.design.GlassTooltip
import octo.design.Pyrlyn
import octo.design.Text
import octo.feature.common.label
import pyrlyn.macos.Mac

/** One message on the map: where it sits in the transcript and how it reads in a tooltip. */
internal data class MessageMark(
    val index: Int,
    val user: Boolean,
    val preview: String,
)

/** A mark per prompt and reply; tool calls, thoughts and the rest would drown the conversation out. */
internal fun messageMarks(entries: List<TranscriptEntry>): List<MessageMark> =
    entries.mapIndexedNotNull { index, entry ->
        (entry as? TranscriptEntry.Text)?.let { MessageMark(index, it.role == Role.User, preview(it)) }
    }

/** The message on one line, cut short; a message of files alone names them instead. */
internal fun preview(entry: TranscriptEntry.Text): String {
    val line = entry.text.trim().replace(Whitespace, " ")
    val text = if (line.length > PREVIEW_CHARS) line.take(PREVIEW_CHARS).trimEnd() + "…" else line
    return text.ifEmpty { entry.attachments.label() }
}

/** The marks of [marks] beside the transcript that [list] scrolls; a click on one scrolls to its message. */
@Composable
internal fun MessageMap(
    marks: List<MessageMark>,
    list: LazyListState,
    modifier: Modifier = Modifier,
) {
    // The macOS design draws text from its own palette, where the shared one reads as a flat grey.
    val bright = if (Pyrlyn.mac) Mac.colors.textPrimary else Pyrlyn.colors.fg
    // Both palettes' muted greys sit too close to their text colour for the marks on screen to stand out.
    val dim = bright.copy(alpha = OFF_SCREEN_ALPHA)
    val scope = rememberCoroutineScope()
    val onScreen by remember(list) {
        derivedStateOf {
            val visible = list.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) IntRange.EMPTY else visible.first().index..visible.last().index
        }
    }
    // A short map sits in the middle like the screen's own scrollbar; a long one scrolls on its own.
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            marks.forEach { mark ->
                GlassTooltip(
                    tip = { Text(mark.preview, color = bright, style = Pyrlyn.type.small, maxLines = PREVIEW_LINES) },
                ) {
                    Box(
                        Modifier
                            .size(MapWidth, MarkRow)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { scope.launch { list.animateScrollToItem(mark.index) } },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            Modifier
                                .size(if (mark.user) UserMark else ReplyMark, MarkThickness)
                                .background(if (mark.index in onScreen) bright else dim, MarkShape),
                        )
                    }
                }
            }
        }
    }
}

private val Whitespace = Regex("\\s+")
private const val PREVIEW_CHARS = 280
private const val PREVIEW_LINES = 6
private val MapWidth = 24.dp
private val MarkRow = 8.dp
private val UserMark = 16.dp
private val ReplyMark = 9.dp
private val MarkThickness = 2.dp
private val MarkShape = RoundedCornerShape(1.dp)
private const val OFF_SCREEN_ALPHA = 0.35f
