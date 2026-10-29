package octo.core.config

import kotlinx.serialization.Serializable

/** How the window looks; the UI applies it as soon as it is saved. */
@Serializable
data class AppearanceConfig(
    val theme: AppearanceTheme = AppearanceTheme.System,
    val transparency: Transparency = Transparency.Glass,
    val density: RowDensity = RowDensity.Normal,
)

enum class AppearanceTheme(
    val label: String,
) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class Transparency(
    val label: String,
) {
    Glass("Glass"),

    /** Solid surfaces, for readability or a slow GPU. */
    Opaque("Opaque"),
}

enum class RowDensity(
    val label: String,
    /** Added to the platform's row and control heights. */
    val stepDp: Int,
) {
    Compact("Compact", -DENSITY_STEP),
    Normal("Normal", 0),
    Comfortable("Comfortable", DENSITY_STEP),
}

private const val DENSITY_STEP = 4
