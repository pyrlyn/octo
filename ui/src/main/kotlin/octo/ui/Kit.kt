package octo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import octo.ui.theme.Elevation
import octo.ui.theme.GlassLevel
import octo.ui.theme.Pyrlyn
import octo.ui.theme.glass
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.TextButton as M3TextButton

private val ButtonPadding = PaddingValues(horizontal = 12.dp)
private val DialogPadding = 22.dp
private val DialogMinWidth = 440.dp
private val DialogMaxWidth = 620.dp
private val EmptyIconSize = 64.dp
private val EmptyIconRadius = 18.dp
private val NoticeRadius = 10.dp
private val MenuRadius = 12.dp
private val MenuShadow = 12.dp
private const val TINT_DARK = 0.16f
private const val TINT_LIGHT = 0.12f
private const val TINT_BORDER = 0.45f
private const val LABEL_SCALE = 0.9f

/** The main action of a view: accent fill. */
@Composable
internal fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    FilledButton(onClick, modifier, enabled, c.accentSolid, c.onAccent, content)
}

/** A raised, opaque control for the other actions that sit next to the primary one. */
@Composable
internal fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    FilledButton(onClick, modifier, enabled, c.bgRaised, c.fg, content)
}

/** Destroys work: delete anyway, full access. */
@Composable
internal fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledButton(onClick, modifier, enabled, Pyrlyn.colors.danger, Color.White, content)
}

/** A label-only action: toolbars, dialog dismissals, links. */
@Composable
internal fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A toggle that is on, such as the chosen permission mode. */
    selected: Boolean = false,
    contentColor: Color = Pyrlyn.colors.fg,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    M3TextButton(
        onClick = onClick,
        modifier = modifier.height(Pyrlyn.platform.controlHeight),
        enabled = enabled,
        shape = Pyrlyn.controlShape,
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = if (selected) c.stateSelected else Color.Transparent,
                contentColor = if (selected) c.stateSelectedFg else contentColor,
                disabledContentColor = c.fgDisabled,
            ),
        contentPadding = ButtonPadding,
        content = content,
    )
}

@Composable
private fun FilledButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    fg: Color,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    M3Button(
        onClick = onClick,
        // A fixed height wins over Material's 40dp minimum, which is too tall for desktop rows.
        modifier = modifier.height(Pyrlyn.platform.controlHeight),
        enabled = enabled,
        shape = Pyrlyn.controlShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = fg,
                disabledContainerColor = c.bgSunken,
                disabledContentColor = c.fgDisabled,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(c.specular.copy(alpha = 0.5f), c.glassEdge))),
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** [androidx.compose.material3.AlertDialog]'s slots on a floating glass panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val c = Pyrlyn.colors
    BasicAlertDialog(onDismissRequest, modifier.widthIn(DialogMinWidth, DialogMaxWidth)) {
        Column(
            Modifier
                .glass(MaterialTheme.shapes.extraLarge, GlassLevel.Floating, Elevation.E3)
                .padding(DialogPadding),
            verticalArrangement = Arrangement.spacedBy(LargeGap),
        ) {
            title?.let {
                CompositionLocalProvider(
                    LocalTextStyle provides Pyrlyn.type.title,
                    LocalContentColor provides c.fg,
                ) { it() }
            }
            text?.let {
                CompositionLocalProvider(LocalTextStyle provides Pyrlyn.type.body, LocalContentColor provides c.fg) {
                    Box(Modifier.weight(1f, fill = false)) { it() }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Gap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** A dropdown on floating glass; takes [DropdownMenu]'s main arguments. */
@Composable
internal fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    DropdownMenu(
        expanded,
        onDismissRequest,
        modifier,
        shape = RoundedCornerShape(minOf(Pyrlyn.platform.radiusSurface, MenuRadius)),
        containerColor = c.veilFloating,
        shadowElevation = MenuShadow,
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(c.glassRim, c.glassEdge))),
        content = content,
    )
}

internal enum class Tone { Info, Warning, Error }

@Composable
private fun Tone.color(): Color =
    when (this) {
        Tone.Info -> Pyrlyn.colors.info
        Tone.Warning -> Pyrlyn.colors.warning
        Tone.Error -> Pyrlyn.colors.danger
    }

private val Tone.glyph: String
    get() =
        when (this) {
            Tone.Info -> "i"
            Tone.Warning -> "⚠"
            Tone.Error -> "⊗"
        }

/** A tinted strip with a glyph, so the tone never rests on colour alone. */
@Composable
internal fun Notice(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Pyrlyn.colors
    val color = tone.color()
    val shape = RoundedCornerShape(NoticeRadius)
    Row(
        modifier
            .fillMaxWidth()
            .background(color.copy(alpha = if (c.dark) TINT_DARK else TINT_LIGHT), shape)
            .border(1.dp, color.copy(alpha = TINT_BORDER), shape)
            .padding(horizontal = 12.dp, vertical = SmallGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tone.glyph, color = color, fontWeight = FontWeight.Bold)
        Text(text, Modifier.weight(1f).padding(vertical = SmallGap), color = c.fg)
        action?.invoke(this)
    }
}

/** A window-wide message with a way to clear it. */
@Composable
internal fun Banner(
    text: String,
    error: Boolean,
    onDismiss: () -> Unit,
) {
    Notice(text, if (error) Tone.Error else Tone.Warning) {
        GhostButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

/** The header of a full-pane screen (diff, agents, usage) and the hairline under it. */
@Composable
internal fun PaneHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PanePadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Pyrlyn.type.title)
            subtitle?.let { Text(it, style = Pyrlyn.type.small, color = Pyrlyn.colors.fgMuted) }
        }
        actions()
    }
    Hairline()
}

@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Pyrlyn.colors.glassEdge))
}

/** The sunken strip over a table with its upper-case column names. */
@Composable
internal fun TableHeader(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Pyrlyn.colors.bgSunken).padding(horizontal = PanePadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(LargeGap),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides tableLabel(),
            LocalContentColor provides Pyrlyn.colors.fgSubtle,
        ) { content() }
    }
}

@Composable
private fun tableLabel() = Pyrlyn.type.label.copy(fontSize = Pyrlyn.type.small.fontSize * LABEL_SCALE)

/** A table row padded to line up with [TableHeader], with a hairline under it. */
@Composable
internal fun TableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PanePadding, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(LargeGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
    Hairline()
}

/** An upper-case label over a group of rows. */
@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text.uppercase(), modifier, style = tableLabel(), color = Pyrlyn.colors.fgSubtle)
}

/** A pane with nothing to show: a glass tile, what is missing, and what to do about it. */
@Composable
internal fun EmptyState(
    icon: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(LargeGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(EmptyIconSize).glass(RoundedCornerShape(EmptyIconRadius), GlassLevel.Control, Elevation.E2),
            contentAlignment = Alignment.Center,
        ) { Text(icon, style = Pyrlyn.type.title, color = Pyrlyn.colors.accentFg) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = Pyrlyn.type.title)
        Text(body, Modifier.padding(top = SmallGap), color = Pyrlyn.colors.fgMuted)
        action?.let {
            Spacer(Modifier.height(14.dp))
            it()
        }
    }
}

/** A filled bar for a share from 0 to 1, such as how full a context window is. */
@Composable
internal fun Meter(
    share: Float,
    modifier: Modifier = Modifier,
) {
    val c = Pyrlyn.colors
    Box(modifier.height(MeterHeight).clip(CircleShape).background(c.bgActive)) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(c.accentSolid))
    }
}

/** The accent gradient tile behind the Octo mark and agent monograms. */
@Composable
internal fun AccentTile(
    text: String,
    size: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val c = Pyrlyn.colors
    val shape = RoundedCornerShape(size / TILE_CORNER_SHARE)
    Box(
        modifier
            .size(size)
            .background(Brush.verticalGradient(listOf(c.accentSolidHover, c.accentSolid)), shape)
            .border(0.5.dp, Color.White.copy(alpha = TILE_RIM), shape),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style, color = c.onAccent) }
}

private val MeterHeight = 4.dp
private const val TILE_CORNER_SHARE = 3.6f
private const val TILE_RIM = 0.35f
