// The macOS look: Pyrlyn's colour and type slots filled from MacOS-Pyrlyn-UI, so every screen that reads
// Pyrlyn.colors and Pyrlyn.type takes the macOS design without knowing about it.
package octo.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import pyrlyn.macos.MacColors
import pyrlyn.macos.MacType

internal fun macPyrlynColors(m: MacColors): PyrlynColors {
    // Pyrlyn's solid slots stand for what the fills look like on the editor ground, taken at full strength
    // so raised panels stay solid when the window's own grounds let the desktop through.
    val ground = m.bgEditor.copy(alpha = 1f)
    val elevated = m.bgElevated.compositeOver(ground)
    return PyrlynColors(
        dark = m.dark,
        bgCanvas = m.bgEditor,
        bgApp = m.bgEditor,
        bgRaised = elevated,
        bgSunken = m.bgFill.compositeOver(ground),
        bgHover = m.bgFill.compositeOver(ground),
        bgActive = m.bgFillMedium.compositeOver(ground),
        border = m.borderSubtle.compositeOver(ground),
        borderStrong = m.borderStrong.compositeOver(ground),
        fg = m.textPrimary,
        fgMuted = m.textSecondary,
        fgSubtle = m.textGhost,
        fgDisabled = m.textGhost.copy(alpha = DISABLED),
        accentSolid = m.accent,
        accentSolidHover = m.accentInk.copy(alpha = HOVER_MIX).compositeOver(m.accent),
        onAccent = m.textOnAccent,
        accentFg = m.accentInk,
        accentBg = m.bgSelection.compositeOver(ground),
        success = m.statusOk,
        warning = m.statusWarning,
        danger = m.statusError,
        info = m.accentInk,
        wallpaper1 = m.bgChrome,
        wallpaper2 = m.bgChrome,
        wallpaper3 = m.bgChrome,
        wallpaper4 = m.bgChrome,
        glassTint = Color.Transparent,
        glassEdge = m.borderHairline,
        glassRim = m.borderSubtle,
        veilChrome = m.bgWindow,
        veilFloating = m.bgElevated,
        veilControl = m.bgFill,
        veilContent = m.bgEditor,
        solidChrome = m.bgWindow.compositeOver(m.bgChrome),
        solidFloating = elevated,
        specular = m.highlight,
        specularSoft = Color.Transparent,
        caustic = Color.Transparent,
        stateHover = m.bgFill,
        stateSelected = m.bgSelection,
        stateSelectedRim = Color.Transparent,
        stateSelectedFg = m.textPrimary,
        shadowContact = m.shadowNear,
        shadowKey = m.shadowNear,
        shadowAmbient = m.shadowFar,
        scrim = m.scrim,
    )
}

internal fun macPyrlynType(t: MacType): PyrlynType =
    PyrlynType(
        body = t.body,
        strong = t.bodyEmphasized,
        small = t.small,
        label = t.sectionLabel,
        title = t.title,
        mono = t.code,
    )

private const val DISABLED = 0.6f
private const val HOVER_MIX = 0.2f
