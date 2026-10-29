package octo.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** The colour text and glyphs take when they set none, such as white on an accent fill. */
val LocalContentColor = compositionLocalOf { Color.Unspecified }

/** The style text takes when it sets none, such as the title style in a dialog's title slot. */
val LocalTextStyle = compositionLocalOf { TextStyle.Default }

/** Sets the text style and colour for everything in [content] that does not set its own. */
@Composable
fun ProvideContent(
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTextStyle provides style, LocalContentColor provides color, content = content)
}

/** Text in Pyrlyn's type: anything it does not set comes from [LocalTextStyle] and [LocalContentColor]. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    BasicText(
        text,
        modifier,
        style.resolve(color, fontSize, fontStyle, fontWeight, fontFamily, textAlign),
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    BasicText(
        text,
        modifier,
        style.resolve(color, fontSize, fontStyle, fontWeight, fontFamily, textAlign),
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
private fun TextStyle.resolve(
    color: Color,
    fontSize: TextUnit,
    fontStyle: FontStyle?,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    textAlign: TextAlign?,
): TextStyle =
    merge(
        TextStyle(
            color = color.takeOrElse { this.color.takeOrElse { LocalContentColor.current } },
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign ?: TextAlign.Unspecified,
        ),
    )
