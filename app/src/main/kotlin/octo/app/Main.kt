package octo.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import octo.core.paths.OctoPaths
import octo.core.paths.currentHostOs
import octo.core.process.JvmProcessLauncher
import octo.ui.OctoApp
import octo.ui.vm.ViewModels

fun main() {
    val paths = OctoPaths.resolve(System.getenv(), currentHostOs(), System.getProperty("user.home"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val octo = runBlocking { Octo.open(paths, JvmProcessLauncher(), scope) }
    val models = ViewModels(octo.core, octo.catalog, scope, octo.warnings, octo.publisher)
    application {
        Window(
            onCloseRequest = {
                runBlocking { octo.close() }
                exitApplication()
            },
            title = "Octo",
        ) {
            OctoApp(models)
        }
    }
}
