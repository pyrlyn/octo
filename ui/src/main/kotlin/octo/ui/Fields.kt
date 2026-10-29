package octo.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import octo.ui.theme.Pyrlyn

/**
 * Pyrlyn's text field: the label sits above a compact field instead of floating in its border,
 * and the field is as tall as a button on this platform. [framed] off drops the outline and fill
 * for a field that already sits on its own glass, such as the prompt box.
 */
@Composable
internal fun GlassField(
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
) {
    val c = Pyrlyn.colors
    val interaction = remember { MutableInteractionSource() }
    val style = Pyrlyn.type.body.copy(color = if (enabled) c.fg else c.fgDisabled)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        label?.let {
            CompositionLocalProvider(
                LocalTextStyle provides Pyrlyn.type.label.copy(color = c.fgMuted),
                content = it,
            )
        }
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
            Decoration(
                value,
                inner,
                singleLine,
                enabled,
                isError,
                interaction,
                placeholder,
                trailingIcon,
                supportingText,
                framed,
            )
        }
    }
}

@Composable
private fun Decoration(
    value: String,
    inner: @Composable () -> Unit,
    singleLine: Boolean,
    enabled: Boolean,
    isError: Boolean,
    interaction: MutableInteractionSource,
    placeholder: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    supportingText: @Composable (() -> Unit)?,
    framed: Boolean,
) {
    val colors = fieldColors(framed)
    OutlinedTextFieldDefaults.DecorationBox(
        value = value,
        innerTextField = inner,
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = VisualTransformation.None,
        interactionSource = interaction,
        isError = isError,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        colors = colors,
        contentPadding = FieldPadding,
        container = {
            OutlinedTextFieldDefaults.Container(
                enabled,
                isError,
                interaction,
                colors = colors,
                shape = Pyrlyn.controlShape,
                unfocusedBorderThickness = 0.5.dp,
            )
        },
    )
}

@Composable
private fun fieldColors(framed: Boolean): TextFieldColors {
    val c = Pyrlyn.colors
    val border = if (framed) c.border else Color.Transparent
    val fill = if (framed) c.veilControl else Color.Transparent
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (framed) c.accentSolid else Color.Transparent,
        unfocusedBorderColor = border,
        disabledBorderColor = border,
        errorBorderColor = c.danger,
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = fill,
        focusedPlaceholderColor = c.fgSubtle,
        unfocusedPlaceholderColor = c.fgSubtle,
        focusedTrailingIconColor = c.fgMuted,
        unfocusedTrailingIconColor = c.fgMuted,
        focusedSupportingTextColor = c.fgMuted,
        unfocusedSupportingTextColor = c.fgMuted,
        errorSupportingTextColor = c.danger,
    )
}

private val FieldPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
