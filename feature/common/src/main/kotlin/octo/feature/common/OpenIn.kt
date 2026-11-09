// Opening a folder in another app: the apps each OS may have, which of them are installed, and how each takes a folder.
package octo.feature.common

import octo.core.paths.HostOs
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What an app is for; the menu draws a glyph by it when the OS gives no icon. */
enum class AppKind { Files, Terminal, Editor }

/**
 * An installed app that can open a folder. [icon] is the file whose OS icon the menu shows, null when the app was found
 * only as a command.
 */
data class OpenApp(
    val id: String,
    val name: String,
    val kind: AppKind,
    val icon: String?,
    internal val launch: Launch,
)

/**
 * How an app opens a folder: [command] with [FOLDER] replaced by the folder's path, run in the folder when [inFolder]
 * (terminals start where they are started, while few take the folder as an argument). An empty command hands the
 * folder to the OS, which opens it in the file manager.
 */
data class Launch(
    val command: List<String>,
    val inFolder: Boolean = false,
) {
    fun forFolder(folder: String): List<String> = command.map { if (it == FOLDER) folder else it }

    companion object {
        const val FOLDER = "{folder}"
        val SYSTEM = Launch(emptyList())
    }
}

/** Finds the installed apps of [os] and opens folders in them. */
class AppOpener(
    private val os: HostOs,
    private val home: String,
    private val env: Map<String, String>,
    private val exists: (String) -> Boolean = { File(it).exists() },
    private val start: (List<String>, String?) -> Unit = ::startDetached,
    private val system: (String) -> Unit = ::openWithSystem,
) {
    /** The apps found on this machine, in the order of the menu; the file manager always leads. */
    fun installed(): List<OpenApp> =
        when (os) {
            HostOs.MacOs -> macApps()
            HostOs.Windows -> windowsApps()
            HostOs.Linux -> linuxApps()
        }

    /** Throws when the app cannot start, with the reason the OS gave. */
    fun open(
        app: OpenApp,
        folder: String,
    ) {
        val launch = app.launch
        if (launch.command.isEmpty()) {
            system(folder)
        } else {
            start(launch.forFolder(folder), folder.takeIf { launch.inFolder })
        }
    }

    private fun macApps(): List<OpenApp> {
        val roots =
            listOf("/Applications", "$home/Applications", "/System/Applications", "/System/Applications/Utilities")
        // A bare `open` of a folder shows it in Finder.
        val finder = OpenApp("finder", "Finder", AppKind.Files, MAC_FINDER, Launch(listOf("open", Launch.FOLDER)))
        // `open -a` hands the folder over the way a drop on the Dock icon does, which every one of these takes.
        val bundles =
            MAC_APPS.mapNotNull { app ->
                roots
                    .flatMap { root -> app.where.map { "$root/$it" } }
                    .firstOrNull(exists)
                    ?.let { app.at(it, Launch(listOf("open", "-a", it, Launch.FOLDER))) }
            }
        return listOf(finder) + bundles
    }

    private fun windowsApps(): List<OpenApp> {
        val local = env["LOCALAPPDATA"] ?: "$home\\AppData\\Local"
        val programs = env["ProgramFiles"] ?: "C:\\Program Files"
        val windir = env["SystemRoot"] ?: "C:\\Windows"
        val explorer = OpenApp("explorer", "File Explorer", AppKind.Files, "$windir\\explorer.exe", Launch.SYSTEM)
        val terminal =
            onPath("wt.exe")?.let {
                OpenApp("terminal", "Windows Terminal", AppKind.Terminal, it, Launch(listOf(it, "-d", Launch.FOLDER)))
            }
        val roots = mapOf("%LOCALAPPDATA%" to local, "%ProgramFiles%" to programs)
        val editors =
            WINDOWS_APPS.mapNotNull { app ->
                app.where
                    .map { path -> roots.entries.fold(path) { acc, (key, value) -> acc.replace(key, value) } }
                    .firstOrNull(exists)
                    ?.let { app.at(it, Launch(listOf(it, Launch.FOLDER))) }
            }
        return listOfNotNull(explorer, terminal) + editors
    }

    private fun linuxApps(): List<OpenApp> {
        val files = OpenApp("files", "Files", AppKind.Files, null, Launch.SYSTEM)
        val terminal =
            LINUX_TERMINALS.firstNotNullOfOrNull { command ->
                onPath(command)?.let { OpenApp("terminal", "Terminal", AppKind.Terminal, null, startIn(it)) }
            }
        val apps =
            LINUX_APPS.mapNotNull { app ->
                app.where.firstNotNullOfOrNull(::onPath)?.let {
                    val launch = if (app.kind == AppKind.Terminal) startIn(it) else Launch(listOf(it, Launch.FOLDER))
                    app.at(it, launch, icon = null)
                }
            }
        return listOfNotNull(files, terminal) + apps
    }

    // A Linux terminal opens where it starts, while few of them take the folder as an argument.
    private fun startIn(command: String) = Launch(listOf(command), inFolder = true)

    private fun onPath(command: String): String? {
        val separator = if (os == HostOs.Windows) ";" else ":"
        val slash = if (os == HostOs.Windows) "\\" else "/"
        return env["PATH"]
            .orEmpty()
            .split(separator)
            .filter { it.isNotBlank() }
            .map { "${it.trimEnd('/', '\\')}$slash$command" }
            .firstOrNull(exists)
    }
}

/** An app Octo knows, and the places it may be: bundle names on macOS, paths on Windows, commands on Linux. */
private class KnownApp(
    val id: String,
    val name: String,
    val kind: AppKind,
    val where: List<String>,
) {
    constructor(id: String, name: String, kind: AppKind, where: String) : this(id, name, kind, listOf(where))

    fun at(
        path: String,
        launch: Launch,
        icon: String? = path,
    ) = OpenApp(id, name, kind, icon, launch)
}

private const val MAC_FINDER = "/System/Library/CoreServices/Finder.app"

private val MAC_APPS =
    listOf(
        KnownApp("terminal", "Terminal", AppKind.Terminal, "Terminal.app"),
        KnownApp("vscode", "VS Code", AppKind.Editor, "Visual Studio Code.app"),
        KnownApp("cursor", "Cursor", AppKind.Editor, "Cursor.app"),
        KnownApp("zed", "Zed", AppKind.Editor, "Zed.app"),
        KnownApp("xcode", "Xcode", AppKind.Editor, "Xcode.app"),
        KnownApp("android-studio", "Android Studio", AppKind.Editor, "Android Studio.app"),
        KnownApp("rustrover", "RustRover", AppKind.Editor, "RustRover.app"),
        KnownApp("iterm", "iTerm", AppKind.Terminal, "iTerm.app"),
        KnownApp("warp", "Warp", AppKind.Terminal, "Warp.app"),
    )

// Per-user installs land under %LOCALAPPDATA%, machine-wide ones under %ProgramFiles%.
private val WINDOWS_APPS =
    listOf(
        KnownApp(
            "vscode",
            "VS Code",
            AppKind.Editor,
            listOf(
                "%LOCALAPPDATA%\\Programs\\Microsoft VS Code\\Code.exe",
                "%ProgramFiles%\\Microsoft VS Code\\Code.exe",
            ),
        ),
        KnownApp("cursor", "Cursor", AppKind.Editor, listOf("%LOCALAPPDATA%\\Programs\\cursor\\Cursor.exe")),
        KnownApp("zed", "Zed", AppKind.Editor, listOf("%LOCALAPPDATA%\\Programs\\Zed\\Zed.exe")),
        KnownApp(
            "android-studio",
            "Android Studio",
            AppKind.Editor,
            listOf("%ProgramFiles%\\Android\\Android Studio\\bin\\studio64.exe"),
        ),
        KnownApp(
            "rustrover",
            "RustRover",
            AppKind.Editor,
            listOf(
                "%LOCALAPPDATA%\\Programs\\RustRover\\bin\\rustrover64.exe",
                "%ProgramFiles%\\JetBrains\\RustRover\\bin\\rustrover64.exe",
            ),
        ),
    )

private val LINUX_TERMINALS =
    listOf("gnome-terminal", "konsole", "xfce4-terminal", "kitty", "alacritty", "x-terminal-emulator")

private val LINUX_APPS =
    listOf(
        KnownApp("vscode", "VS Code", AppKind.Editor, listOf("code")),
        KnownApp("cursor", "Cursor", AppKind.Editor, listOf("cursor")),
        KnownApp("zed", "Zed", AppKind.Editor, listOf("zed", "zeditor")),
        KnownApp("android-studio", "Android Studio", AppKind.Editor, listOf("android-studio", "studio.sh")),
        KnownApp("rustrover", "RustRover", AppKind.Editor, listOf("rustrover", "rustrover.sh")),
        KnownApp("warp", "Warp", AppKind.Terminal, listOf("warp-terminal")),
    )

/**
 * Starts [command] without tying it to Octo. A launcher such as `open` that fails says so at once, so a quick non-zero
 * exit is reported; an app that keeps running is left to run.
 */
internal fun startDetached(
    command: List<String>,
    folder: String?,
) {
    val process =
        ProcessBuilder(command)
            .apply { folder?.let { directory(File(it)) } }
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectErrorStream(false)
            .start()
    process.outputStream.close()
    if (process.waitFor(LAUNCH_WAIT_SECONDS, TimeUnit.SECONDS) && process.exitValue() != 0) {
        val reason =
            process.errorStream
                .bufferedReader()
                .readText()
                .trim()
        throw IOException(reason.ifEmpty { "${command.first()} exited with ${process.exitValue()}" })
    }
}

/** Opens a file or folder in whatever the OS picks for it. */
fun openWithSystem(path: String) {
    val supported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
    if (!supported) throw UnsupportedOperationException("This system cannot open $path from Octo")
    Desktop.getDesktop().open(File(path))
}

private const val LAUNCH_WAIT_SECONDS = 2L
