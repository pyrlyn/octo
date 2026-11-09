package octo.design

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

internal actual val MonoFont: FontFamily =
    FontFamily(
        Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal),
        Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium),
        Font("fonts/JetBrainsMono-Bold.ttf", FontWeight.Bold),
    )
