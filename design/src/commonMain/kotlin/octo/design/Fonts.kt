package octo.design

import androidx.compose.ui.text.font.FontFamily

/** IBM Plex Sans (SIL OFL 1.1) ships with the app, so every OS reads the same. */
internal expect val UiFont: FontFamily

/** IBM Plex Mono (SIL OFL 1.1), the one monospaced face: code, paths, key caps and live counters. */
expect val MonoFont: FontFamily
