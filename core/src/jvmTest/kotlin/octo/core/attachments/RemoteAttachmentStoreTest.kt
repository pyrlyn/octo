package octo.core.attachments

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.process.JvmProcessLauncher
import octo.core.task.TaskId
import octo.core.tempDir
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs the remote store's shell commands on this computer, as ssh would run them there. */
class RemoteAttachmentStoreTest {
    private val windows = System.getProperty("os.name").lowercase().contains("windows")
    private val launcher = JvmProcessLauncher(mapOf("PATH" to (System.getenv("PATH") ?: "")))
    private val root = tempDir("octo-attachments").toString()
    private val store = RemoteAttachmentStore(launcher) { root }
    private val task = TaskId("t1")

    private fun exists(path: String) = SystemFileSystem.exists(Path(path))

    @Test
    fun aFileCrossesAsBase64AndComesBackTheSame() =
        runBlocking {
            if (windows) return@runBlocking
            // Long enough for several base64 lines, with every byte value in it.
            val bytes = Random(1).nextBytes(5_000) + ByteArray(256) { it.toByte() }

            val path = store.save(task, "it's a \"shot\".png", bytes)
            assertTrue(path.startsWith("$root/t1/") && path.endsWith("-it_s_a_shot_.png"), path)
            assertContentEquals(bytes, store.read(path))
        }

    @Test
    fun deletesOnlyItsOwnFiles() =
        runBlocking {
            if (windows) return@runBlocking
            val kept = store.save(task, "a.txt", "a".encodeToByteArray())
            val gone = store.save(task, "b.txt", "b".encodeToByteArray())
            val outside = Path(tempDir("octo-outside"), "keep.txt").toString()
            SystemFileSystem.sink(Path(outside)).close()

            store.delete(listOf(gone, outside, "$root/t1/../../keep.txt"))
            assertTrue(exists(kept))
            assertFalse(exists(gone))
            assertTrue(exists(outside), "a path outside the attachment folders is never touched")
            assertFailsWith<IllegalArgumentException> { store.read(outside) }

            store.deleteAll(task)
            assertFalse(exists("$root/t1"))
        }
}
