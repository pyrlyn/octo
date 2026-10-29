package octo.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import octo.core.config.ConfigFile
import octo.core.flags.CoreFlags
import octo.core.io.AtomicFile
import octo.core.paths.OctoPaths
import octo.core.paths.currentHostOs
import octo.core.process.JvmProcessLauncher
import octo.feature.diff.FilePreviewer
import octo.feature.palette.isPaletteShortcut
import octo.ui.NotificationTray
import octo.ui.OctoApp
import octo.ui.vm.ViewModels

fun main() {
    val paths = OctoPaths.resolve(System.getenv(), currentHostOs(), System.getProperty("user.home"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val homes = AgentHomes.resolve(System.getenv(), System.getProperty("user.home"))
    val launcher = JvmProcessLauncher()
    val octo = runBlocking { Octo.open(paths, launcher, scope, homes) }
    val models =
        ViewModels(
            octo.core,
            octo.catalog,
            scope,
            octo.warnings,
            octo.publisher,
            octo.presets,
            octo.devices,
            octo.remoteUrl,
            octo.plugins,
            octo.handoff,
            octo.fork,
            octo.compare,
            octo.sections,
            octo.palette,
            octo.usage,
            octo.checkpoints,
            octo.moveTask,
            if (octo.previews) FilePreviewer(launcher) else null,
            octo.shares,
            octo.shareBase,
            octo.notifications,
            ConfigFile(AtomicFile(paths.config)),
            CoreFlags.all + builtInAdapters(launcher, currentHostOs()).mapNotNull { it.featureFlag },
            paths.home.toString(),
        )
    application {
        Window(
            onCloseRequest = {
                runBlocking { octo.close() }
                exitApplication()
            },
            title = "Octo",
            onPreviewKeyEvent = { event ->
                (octo.palette && isPaletteShortcut(event)).also { if (it) models.main.togglePalette() }
            },
        ) {
            models.notifications?.let { this@application.NotificationTray(it, onOpen = window::toFront) }
            OctoApp(models)
        }
    }
}
