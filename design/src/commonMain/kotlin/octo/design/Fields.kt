package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pyrlyn's text field: the label sits above a compact field instead of floating in its border,
 * and the field is as tall as a button on this platform. [framed] off drops the outline and fill
 * for a field that already sits on its own glass, such as the prompt box.
 */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    framed: Boolean = true,
    /** A list that drops from the field box, such as matching branches; it gets the box's width. */
    menu: @Composable ((width: Dp) -> Unit)? = null,
) {
    val c = Pyrlyn.colors
    val interaction = remember { MutableInteractionSource() }
    val style = Pyrlyn.type.body.copy(color = if (enabled) c.fg else c.fgDisabled)
    FieldColumn(modifier, label, supportingText, isError) {
        MenuAnchor(menu) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Pyrlyn.platform.controlHeight),
                enabled = enabled,
                readOnly = readOnly,
                textStyle = style,
                cursorBrush = SolidColor(c.accentSolid),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interaction,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
            ) { inner ->
                FieldFrame(interaction, enabled, isError, framed, singleLine) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() &&
                            placeholder != null
                        ) {
                            ProvideContent(Pyrlyn.type.body, c.fgSubtle, placeholder)
                        }
                        inner()
                    }
                    trailingIcon?.let { ProvideContent(color = c.fgMuted, content = it) }
                }
            }
        }
    }
}

/**
 * A field-shaped select over [options]. The list is the control's own state, so the screen only
 * hears [onSelect]. An option that is not [optionEnabled] is still listed, so the user sees it exists.
 */
@Composable
fun <T> GlassSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    optionNote: ((T) -> String)? = null,
    optionEnabled: (T) -> Boolean = { true },
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    FieldColumn(
        modifier,
        label?.let { { Text(it) } },
        supportingText?.let { { Text(it) } },
        isError,
    ) {
        MenuAnchor({ width ->
            GlassMenu(open, { open = false }, Modifier.width(width)) {
                options.forEach { option ->
                    GlassMenuItem(
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                        enabled = optionEnabled(option),
                    ) {
                        Column {
                            Text(optionLabel(option))
                            optionNote?.let { note ->
                                Text(
                                    note(option),
                                    style = Pyrlyn.type.small,
                                    color = LocalContentColor.current.copy(alpha = NOTE_ALPHA),
                                )
                            }
                        }
                    }
                }
            }
        }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Pyrlyn.platform.controlHeight)
                    .clickable(interaction, null, options.isNotEmpty(), role = Role.DropdownList) { open = !open },
            ) {
                FieldFrame(interaction, enabled = true, isError, framed = true, singleLine = true) {
                    // By position, so a null option such as "this computer" still shows its label.
                    val shown =
                        options.indexOfFirst { it == selected }.takeIf { it >= 0 }?.let {
                            optionLabel(
                                options[it],
                            )
                        }
                    Text(
                        shown.orEmpty(),
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Chevron(open)
                }
            }
        }
    }
}

/** The small triangle at the end of a select or a field with a list; it points up while the list is open. */
@Composable
fun Chevron(open: Boolean) {
    val color = Pyrlyn.colors.fgMuted
    Box(
        Modifier.size(ChevronWidth, ChevronHeight).drawBehind {
            val (base, tip) = if (open) size.height to 0f else 0f to size.height
            val arrow =
                Path().apply {
                    moveTo(0f, base)
                    lineTo(size.width / 2, tip)
                    lineTo(size.width, base)
                    close()
                }
            drawPath(arrow, color)
        },
    )
}

/** The design's name over a field or a group of choices: small, medium weight, muted. */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, style = Pyrlyn.type.small.copy(fontWeight = FontWeight.Medium), color = Pyrlyn.colors.fgMuted)
}

@Composable
private fun FieldColumn(
    modifier: Modifier,
    label: @Composable (() -> Unit)?,
    supportingText: @Composable (() -> Unit)?,
    isError: Boolean,
    field: @Composable () -> Unit,
) {
    val c = Pyrlyn.colors
    val small = Pyrlyn.type.small
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        label?.let {
            ProvideContent(
                small.copy(fontWeight = FontWeight.Medium),
                if (isError) c.danger else c.fgMuted,
                it,
            )
        }
        field()
        supportingText?.let { ProvideContent(small, if (isError) c.danger else c.fgSubtle, it) }
    }
}

/** Holds the field box, so a [menu] drops from the box and not from the label or the hint. */
@Composable
private fun MenuAnchor(
    menu: @Composable ((width: Dp) -> Unit)?,
    field: @Composable () -> Unit,
) {
    if (menu == null) return field()
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0.dp) }
    Box(Modifier.onSizeChanged { width = with(density) { it.width.toDp() } }) {
        field()
        menu(width)
    }
}

/** The design's field box: sunken fill, a 1dp edge, and a 2dp accent or danger edge on focus or error. */
@Composable
private fun FieldFrame(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    isError: Boolean,
    framed: Boolean,
    singleLine: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    val shape = Pyrlyn.controlShape
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val frame =
        if (framed) {
            val edge =
                when {
                    isError -> c.danger
                    focused -> c.accentSolid
                    hovered && enabled -> c.fgMuted
                    else -> c.borderStrong.copy(alpha = EDGE_ALPHA)
                }
            Modifier
                .clip(shape)
                .background(if (enabled) c.bgSunken else c.bgSunken.copy(alpha = DISABLED_FILL))
                .border(if (isError || focused) 2.dp else 1.dp, edge, shape)
        } else {
            Modifier
        }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Pyrlyn.platform.controlHeight)
            .hoverable(interaction, enabled)
            .then(frame)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Gap),
        content = content,
    )
}

private val ChevronWidth = 8.dp
private val ChevronHeight = 4.dp
private const val EDGE_ALPHA = 0.7f
private const val DISABLED_FILL = 0.5f
private const val NOTE_ALPHA = 0.72f
