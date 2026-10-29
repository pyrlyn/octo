package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val DialogPadding = 22.dp
private val DialogMinWidth = 440.dp
private val EmptyIconSize = 64.dp
private val EmptyIconRadius = 18.dp
private val NoticeRadius = 10.dp
private val MenuRadius = 12.dp
private val MenuItemRadius = 6.dp
private val MenuMinWidth = 240.dp
private val MenuCheckWidth = 16.dp
private val MenuPadding = 5.dp
private const val TINT_DARK = 0.16f
private const val TINT_LIGHT = 0.12f
private const val TINT_BORDER = 0.45f
private const val LABEL_SCALE = 0.9f
private const val DIALOG_VEIL_DARK = 0.55f
private const val DIALOG_VEIL_LIGHT = 0.6f
private const val SHEEN_DARK = 0.06f
private const val SHEEN_LIGHT = 0.35f
private const val PRESSED_ACCENT = 0.85f

/** The main action of a view: accent fill. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    val pressed =
        c.accentSolid.copy(
            red = c.accentSolid.red * PRESSED_ACCENT,
            green =
                c.accentSolid.green * PRESSED_ACCENT,
            blue = c.accentSolid.blue * PRESSED_ACCENT,
        )
    ButtonBase(
        onClick,
        modifier,
        enabled,
        ButtonFills(c.accentSolid, c.accentSolidHover, pressed),
        c.onAccent,
        raised = true,
        content,
    )
}

/** A raised, opaque control for the other actions that sit next to the primary one. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    ButtonBase(onClick, modifier, enabled, ButtonFills(c.bgRaised, c.bgHover, c.bgActive), c.fg, raised = true, content)
}

/** Destroys work: delete anyway, full access. */
@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val danger = Pyrlyn.colors.danger
    ButtonBase(
        onClick,
        modifier,
        enabled,
        ButtonFills(danger, danger, danger.copy(alpha = PRESSED_ACCENT)),
        Color.White,
        raised = true,
        content,
    )
}

/** A label-only action: toolbars, dialog dismissals, links. */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A toggle that is on, such as the chosen permission mode. */
    selected: Boolean = false,
    contentColor: Color = Pyrlyn.colors.fg,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    val fills =
        if (selected) {
            ButtonFills(c.stateSelected, c.stateSelected, c.stateSelected)
        } else {
            ButtonFills(Color.Transparent, c.stateHover, c.bgActive)
        }
    ButtonBase(
        onClick,
        modifier,
        enabled,
        fills,
        if (selected) c.stateSelectedFg else contentColor,
        raised = false,
        content,
    )
}

private class ButtonFills(
    val rest: Color,
    val hover: Color,
    val press: Color,
)

@Composable
private fun ButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fills: ButtonFills,
    fg: Color,
    raised: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    val p = Pyrlyn.platform
    val shape = Pyrlyn.controlShape
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill =
        when {
            !enabled -> if (raised) c.bgSunken else Color.Transparent
            pressed -> fills.press
            hovered -> fills.hover
            else -> fills.rest
        }
    Row(
        modifier
            .height(p.controlHeight)
            .then(
                if (raised) {
                    Modifier.raised(
                        shape,
                        fill,
                        flat = pressed || !enabled,
                    )
                } else {
                    Modifier.clip(shape).background(fill)
                },
            ).then(if (focused) Modifier.border(FocusWidth, c.accentSolid, shape) else Modifier)
            .clickable(interaction, null, enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (p.controlHeight > ROOMY_CONTROL.dp) 18.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContent(Pyrlyn.type.body.copy(fontWeight = FontWeight.Medium), if (enabled) fg else c.fgDisabled) {
            content()
        }
    }
}

/** An opaque control lifted off the glass: contact shadow, top sheen and a specular rim. */
@Composable
private fun Modifier.raised(
    shape: Shape,
    fill: Color,
    flat: Boolean,
): Modifier {
    val c = Pyrlyn.colors
    return this
        .then(
            if (flat) {
                Modifier
            } else {
                Modifier.shadow(
                    2.dp,
                    shape,
                    ambientColor = c.shadowContact,
                    spotColor = c.shadowKey,
                )
            },
        ).clip(shape)
        .background(fill)
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (c.dark) SHEEN_DARK else SHEEN_LIGHT), Color.Transparent),
            ),
        ).border(1.dp, Brush.verticalGradient(listOf(c.specular.copy(alpha = 0.5f), c.glassEdge)), shape)
}

/** An alert dialog's slots on a floating glass panel. */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val c = Pyrlyn.colors
    // The platform's default width would cap a form dialog below its own width.
    Dialog(onDismissRequest = onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .widthIn(DialogMinWidth, DialogWidth)
                .glass(RoundedCornerShape(Pyrlyn.platform.radiusWindow), GlassLevel.Floating, Elevation.E3)
                .background(veil())
                .padding(DialogPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            title?.let { ProvideContent(Pyrlyn.type.title, c.fg, it) }
            text?.let {
                ProvideContent(Pyrlyn.type.body, c.fg) {
                    Box(Modifier.weight(1f, fill = false)) { it() }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(Gap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/**
 * A dropdown on floating glass under the layout that holds it, or above when there is no room.
 * [focusable] off keeps the keyboard in a field the menu belongs to.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val gap = with(LocalDensity.current) { SmallGap.roundToPx() }
    val focus = LocalFocusManager.current
    Popup(remember(gap) { DropPosition(gap) }, onDismissRequest, PopupProperties(focusable = focusable)) {
        Column(
            modifier
                .widthIn(min = MenuMinWidth)
                .glass(
                    RoundedCornerShape(minOf(Pyrlyn.platform.radiusSurface, MenuRadius)),
                    GlassLevel.Floating,
                    Elevation.E2,
                ).background(veil())
                .onPreviewKeyEvent {
                    // Arrow keys walk the items, as in a native menu; Tab already does.
                    when {
                        it.type != KeyEventType.KeyDown -> false
                        it.key == Key.DirectionDown -> focus.moveFocus(FocusDirection.Next)
                        it.key == Key.DirectionUp -> focus.moveFocus(FocusDirection.Previous)
                        else -> false
                    }
                }.padding(MenuPadding)
                .width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/** One menu entry: accent fill under the pointer, as in the design. */
@Composable
fun GlassMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Destroys work or widens what an agent may do, such as full access. */
    danger: Boolean = false,
    /** Non-null keeps a column for a tick, so a menu of choices lines up; true ticks and tints this one. */
    checked: Boolean? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hot = enabled && (hovered || focused)
    val fg =
        when {
            hot -> c.onAccent
            !enabled -> c.fgDisabled
            danger -> c.danger
            else -> c.fg
        }
    val shape = RoundedCornerShape(MenuItemRadius)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Pyrlyn.platform.controlHeight)
            .clip(shape)
            .background(
                when {
                    hot -> c.accentSolid
                    checked == true -> c.stateSelected
                    else -> Color.Transparent
                },
            ).clickable(interaction, null, enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Gap, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        ProvideContent(Pyrlyn.type.body, fg) {
            checked?.let { Text(if (it) "✓" else "", Modifier.width(MenuCheckWidth), style = Pyrlyn.type.small) }
            content()
        }
    }
}

/** A floating panel's own fill under its glass, so text behind a menu or dialog does not read through it. */
@Composable
private fun veil(): Color {
    val c = Pyrlyn.colors
    return if (Pyrlyn.opaque) {
        Color.Transparent
    } else {
        c.bgRaised.copy(
            alpha = if (c.dark) DIALOG_VEIL_DARK else DIALOG_VEIL_LIGHT,
        )
    }
}

/** Under the anchor and lined up with its start; above it, or pinned inside the window, when it does not fit. */
private class DropPosition(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val y =
            when {
                below + popupContentSize.height <= windowSize.height -> below
                above >= 0 -> above
                else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
            }
        return IntOffset(x, y)
    }
}

private val FocusWidth = 2.dp
private const val ROOMY_CONTROL = 36

enum class Tone { Info, Warning, Error }

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
fun Notice(
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
fun Banner(
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
fun PaneHeader(
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
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(hairline).background(Pyrlyn.colors.glassEdge))
}

/** The sunken strip over a table with its upper-case column names. */
@Composable
fun TableHeader(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Pyrlyn.colors.bgSunken).padding(horizontal = PanePadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(LargeGap),
    ) {
        ProvideContent(tableLabel(), Pyrlyn.colors.fgSubtle) { content() }
    }
}

@Composable
private fun tableLabel() = Pyrlyn.type.label.copy(fontSize = Pyrlyn.type.small.fontSize * LABEL_SCALE)

/** A table row padded to line up with [TableHeader], with a hairline under it. */
@Composable
fun TableRow(content: @Composable RowScope.() -> Unit) {
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
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text.uppercase(), modifier, style = tableLabel(), color = Pyrlyn.colors.fgSubtle)
}

/** A pane with nothing to show: a glass tile, what is missing, and what to do about it. */
@Composable
fun EmptyState(
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
fun Meter(
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
fun AccentTile(
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
            .border(hairline, Color.White.copy(alpha = TILE_RIM), shape),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style, color = c.onAccent) }
}

@Composable
fun Kbd(key: String) {
    val c = Pyrlyn.colors
    val shape = RoundedCornerShape(4.dp)
    Text(
        key,
        Modifier
            .background(c.stateHover, shape)
            .border(hairline, c.glassRim, shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = Pyrlyn.type.mono.copy(fontSize = Pyrlyn.type.small.fontSize),
        color = c.fgMuted,
    )
}

private val MeterHeight = 4.dp
private const val TILE_CORNER_SHARE = 3.6f
private const val TILE_RIM = 0.35f
