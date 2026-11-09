package octo.feature.common

import octo.core.paths.HostOs
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppOpenerTest {
    private val starts = mutableListOf<Pair<List<String>, String?>>()
    private val systemOpens = mutableListOf<String>()

    private fun started(
        command: List<String>,
        folder: String? = null,
    ) = command to folder

    private fun opener(
        os: HostOs,
        files: Set<String>,
        env: Map<String, String> = emptyMap(),
    ) = AppOpener(
        os = os,
        home = "/home/me",
        env = env,
        exists = { it in files },
        start = { command, folder -> starts += command to folder },
        system = { systemOpens += it },
    )

    @Test
    fun macListsFinderFirstAndOnlyTheBundlesItFinds() {
        val apps =
            opener(
                HostOs.MacOs,
                setOf("/System/Applications/Utilities/Terminal.app", "/home/me/Applications/Zed.app"),
            ).installed()

        assertEquals(listOf("finder", "terminal", "zed"), apps.map { it.id })
        assertEquals("/home/me/Applications/Zed.app", apps.last().icon)
    }

    @Test
    fun macOpensTheFolderThroughOpen() {
        val opener = opener(HostOs.MacOs, setOf("/Applications/Cursor.app"))
        val (finder, cursor) = opener.installed()

        opener.open(finder, "/work/tree")
        opener.open(cursor, "/work/tree")

        assertEquals(
            listOf(
                started(listOf("open", "/work/tree")),
                started(listOf("open", "-a", "/Applications/Cursor.app", "/work/tree")),
            ),
            starts,
        )
    }

    @Test
    fun windowsFindsPerUserAndMachineWideInstalls() {
        val local = "C:\\Users\\me\\AppData\\Local"
        val apps =
            opener(
                HostOs.Windows,
                setOf(
                    "$local\\Programs\\cursor\\Cursor.exe",
                    "C:\\Program Files\\Microsoft VS Code\\Code.exe",
                    "C:\\Windows\\System32\\wt.exe",
                ),
                env = mapOf("LOCALAPPDATA" to local, "PATH" to "C:\\Windows\\System32;C:\\Tools"),
            ).installed()

        assertEquals(listOf("explorer", "terminal", "vscode", "cursor"), apps.map { it.id })
        assertEquals("C:\\Program Files\\Microsoft VS Code\\Code.exe", apps[2].icon)
    }

    @Test
    fun windowsHandsTheFolderToExplorerAndTerminal() {
        val opener =
            opener(HostOs.Windows, setOf("C:\\bin\\wt.exe"), env = mapOf("PATH" to "C:\\bin"))
        val (explorer, terminal) = opener.installed()

        opener.open(explorer, "C:\\work")
        opener.open(terminal, "C:\\work")

        assertEquals(listOf("C:\\work"), systemOpens)
        assertEquals(listOf(started(listOf("C:\\bin\\wt.exe", "-d", "C:\\work"))), starts)
    }

    @Test
    fun linuxStartsTheFirstTerminalInTheFolder() {
        val opener =
            opener(
                HostOs.Linux,
                setOf("/usr/bin/konsole", "/usr/bin/kitty", "/opt/zed/bin/zeditor"),
                env = mapOf("PATH" to "/usr/bin:/opt/zed/bin/"),
            )
        val apps = opener.installed()
        val terminal = apps.single { it.id == "terminal" }

        opener.open(terminal, "/work")
        opener.open(apps.single { it.id == "zed" }, "/work")

        assertEquals(listOf("files", "terminal", "zed"), apps.map { it.id })
        assertNull(terminal.icon)
        assertEquals(
            listOf(started(listOf("/usr/bin/konsole"), "/work"), started(listOf("/opt/zed/bin/zeditor", "/work"))),
            starts,
        )
    }

    @Test
    fun aLauncherThatFailsAtOnceReportsWhy() {
        // The failing launcher is a POSIX shell, which Windows runners lack.
        if (File.separatorChar == '\\') return
        val failure = assertFailsWith<IOException> { startDetached(listOf("sh", "-c", "echo nope >&2; exit 3"), null) }

        assertEquals("nope", failure.message)
    }
}
