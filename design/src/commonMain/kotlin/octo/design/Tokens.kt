package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val SmallGap = 4.dp
val Gap = 8.dp
val LargeGap = 16.dp
val DotSize = 8.dp
val PanePadding = 20.dp
val DialogWidth = 560.dp

/** Background of an added diff line. */
@Composable
fun addedLine(): Color = Pyrlyn.colors.success.copy(alpha = DIFF_TINT)

/** Background of a removed diff line. */
@Composable
fun removedLine(): Color = Pyrlyn.colors.danger.copy(alpha = DIFF_TINT)

private const val DIFF_TINT = 0.18f

/** A status is always a dot and a word, so the colour is never the only signal. */
@Composable
fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(DotSize).background(color, CircleShape))
}
