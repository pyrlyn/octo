package octo.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import octo.ui.OctoApp

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Octo") {
            OctoApp()
        }
    }
