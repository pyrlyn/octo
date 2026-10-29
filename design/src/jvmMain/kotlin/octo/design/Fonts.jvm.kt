package octo.design

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

internal actual val UiFont: FontFamily =
    FontFamily(
        Font("fonts/IBMPlexSans-Regular.ttf", FontWeight.Normal),
        Font("fonts/IBMPlexSans-Medium.ttf", FontWeight.Medium),
        Font("fonts/IBMPlexSans-SemiBold.ttf", FontWeight.SemiBold),
        Font("fonts/IBMPlexSans-Bold.ttf", FontWeight.Bold),
    )

internal actual val MonoFont: FontFamily =
    FontFamily(
        Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal),
        Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium),
        Font("fonts/JetBrainsMono-Bold.ttf", FontWeight.Bold),
    )
