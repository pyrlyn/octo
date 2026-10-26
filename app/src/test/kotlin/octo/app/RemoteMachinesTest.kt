package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.io.files.Path
import octo.core.config.MachineConfig
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlags
import octo.core.process.JvmProcessLauncher
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteMachinesTest {
    private val root = Files.createTempDirectory("octo-sockets").toFile().canonicalFile
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val flags = FeatureFlags(mapOf(CoreFlags.REMOTE_SSH.id to true))
    private val box = MachineConfig("box", "me@box")

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun theSocketFolderIsPrivateToTheUser() {
        val dir = File(root, "octo")
        val warnings = mutableListOf<String>()

        assertEquals(dir.path, socketDir(Path(dir.path), warnings))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.toPath())))

        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwxrwxrwx"))
        assertEquals(dir.path, socketDir(Path(dir.path), warnings))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.toPath())))
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun aSocketFolderThatCannotBeTrustedTurnsSharingOff() {
        val file = File(root, "octo").apply { writeText("") }
        val link = File(root, "link").also { Files.createSymbolicLink(it.toPath(), root.toPath()) }
        val warnings = mutableListOf<String>()

        assertNull(socketDir(Path(file.path), warnings))
        assertNull(socketDir(Path(link.path), warnings))

        assertEquals(2, warnings.size, warnings.toString())
        assertTrue(warnings.all { it.startsWith("Remote machines open an SSH connection per call") })
    }

    @Test
    fun aSocketPathSshCannotUseIsReportedAndTheMachineStillWorks() {
        val long = File(root, "o".repeat(LONG_NAME))

        val remote = remoteMachines(listOf(box), JvmProcessLauncher(emptyMap()), flags, Path(long.path), scope)

        assertEquals(setOf("box"), remote.machines.keys)
        assertTrue(remote.warnings.single().startsWith("Machine 'box' opens an SSH connection per call"))
    }

    @Test
    fun sharingIsOffWithoutASocketFolder() {
        val remote = remoteMachines(listOf(box), JvmProcessLauncher(emptyMap()), flags)

        assertEquals(setOf("box"), remote.machines.keys)
        assertEquals(emptyList(), remote.warnings)
    }

    private companion object {
        const val LONG_NAME = 90
    }
}
