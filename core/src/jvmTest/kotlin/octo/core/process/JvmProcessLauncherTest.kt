package octo.core.process

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JvmProcessLauncherTest {
    private val windows = System.getProperty("os.name").lowercase().contains("windows")
    private val launcher = JvmProcessLauncher(mapOf("PATH" to (System.getenv("PATH") ?: ""), "OCTO_TEST" to "base"))

    private fun shell(script: String) =
        if (windows) ProcessSpec("cmd", listOf("/c", script)) else ProcessSpec("sh", listOf("-c", script))

    @Test
    fun runCollectsOutputAndExitCode() =
        runBlocking {
            if (windows) return@runBlocking
            val result = launcher.run(shell("read line; echo \"got \$line\"; echo err >&2; exit 3"), stdin = "hi")
            assertEquals(3, result.exitCode)
            assertEquals("got hi", result.stdout)
            assertEquals("err", result.stderr)
        }

    @Test
    fun envComesOnlyFromBaseAndSpec() =
        runBlocking {
            if (windows) return@runBlocking
            val result =
                launcher.run(
                    shell("echo \"\$OCTO_TEST \$OCTO_EXTRA \${HOME:-nohome}\"").copy(
                        env =
                            mapOf("OCTO_EXTRA" to "spec"),
                    ),
                )
            assertEquals("base spec nohome", result.stdout)
        }

    @Test
    fun whichFindsShellAndNotMissing() =
        runBlocking {
            assertNotNull(launcher.which(if (windows) "cmd" else "sh"))
            assertNull(launcher.which("octo-definitely-missing-binary"))
        }
}
