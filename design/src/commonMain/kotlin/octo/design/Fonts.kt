package octo.design

import androidx.compose.ui.text.font.FontFamily

/** IBM Plex Sans (SIL OFL 1.1) ships with the app, so every OS reads the same. */
internal expect val UiFont: FontFamily

/** JetBrains Mono (SIL OFL 1.1), for code, paths and key caps. */
internal expect val MonoFont: FontFamily
