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

actual val MonoFont: FontFamily =
    FontFamily(
        Font("fonts/IBMPlexMono-Regular.ttf", FontWeight.Normal),
        Font("fonts/IBMPlexMono-Medium.ttf", FontWeight.Medium),
        Font("fonts/IBMPlexMono-Bold.ttf", FontWeight.Bold),
    )
