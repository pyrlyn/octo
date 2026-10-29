package octo.feature.common

import javax.swing.JFileChooser

/** The OS folder chooser, starting at [start] or the home folder; null when cancelled. */
fun pickDirectory(start: String): String? {
    val chooser =
        JFileChooser(start.ifBlank { System.getProperty("user.home") }).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a repository"
        }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.path else null
}
