package octo.core.attachments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.task.TaskId
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AttachmentsTest {
    @Test
    fun namesStayInsideTheirFolder() {
        assertEquals("passwd", Attachments.safeName("../../etc/passwd"))
        assertEquals("evil.sh", Attachments.safeName("C:\\temp\\evil.sh"))
        assertEquals("a_b_c_.png", Attachments.safeName("a b\$c'.png"))
        assertEquals("env", Attachments.safeName(".env"))
        assertEquals("file", Attachments.safeName(".."))
    }

    @Test
    fun namesInOneMessageAreDistinct() {
        val files = listOf("a.png", "a.png", "b", "b").map { FileUpload(it, ByteArray(0)) }
        assertEquals(listOf("a.png", "a-2.png", "b", "b-2"), Attachments.names(files))
    }

    @Test
    fun theTypeComesFromTheBytes() {
        assertEquals("image/png", Attachments.mimeType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0)))
        assertEquals("image/jpeg", Attachments.mimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals("image/gif", Attachments.mimeType("GIF89a".encodeToByteArray()))
        assertEquals("image/webp", Attachments.mimeType("RIFF0000WEBPVP8".encodeToByteArray()))
        assertEquals(Attachments.FALLBACK_TYPE, Attachments.mimeType("not an image".encodeToByteArray()))
    }

    @Test
    fun aLargeImageGoesAsAPath() {
        val small = Attachment("a.png", "/a.png", "image/png", Attachments.MAX_IMAGE_BYTES)
        assertTrue(small.sendsAsImage)
        assertEquals(false, small.copy(size = Attachments.MAX_IMAGE_BYTES + 1).sendsAsImage)
        assertEquals(false, small.copy(mimeType = "image/svg+xml").sendsAsImage)
    }

    @Test
    fun theLimitsAreChecked() {
        assertFailsWith<IllegalArgumentException> {
            Attachments.check(List(Attachments.MAX_FILES + 1) { FileUpload("f", ByteArray(0)) })
        }
        assertFailsWith<IllegalArgumentException> {
            Attachments.check(listOf(FileUpload("big", ByteArray(Attachments.MAX_FILE_BYTES.toInt() + 1))))
        }
    }

    @Test
    fun aRemoteMachineGetsTheBytesAsBase64AndGivesThemBack() =
        runTest {
            val machine = FakeShell()
            val store = RemoteAttachments(machine) { "/home/me/.octo/attachments" }
            val bytes = ByteArray(200_000) { it.toByte() }

            val path = store.save(TaskId("t1"), "b1", "x.bin", bytes)

            assertEquals("/home/me/.octo/attachments/t1/b1/x.bin", path)
            // The path is an argument, never part of the script.
            assertTrue(machine.specs.all { path !in it.args[1] })
            assertContentEquals(bytes, machine.files.getValue(path))
            assertContentEquals(bytes, store.read(path))

            store.delete(path)
            assertTrue(machine.files.isEmpty())
        }

    @Test
    fun aPathFromOutsideTheFolderIsNeverTouched() =
        runTest {
            val machine = FakeShell()
            val store = RemoteAttachments(machine) { "/home/me/.octo/attachments" }

            assertFailsWith<IllegalArgumentException> { store.delete("/home/me/.ssh/id_ed25519") }
            assertFailsWith<IllegalArgumentException> { store.read("/home/me/.octo/attachments/../config.json") }
            assertTrue(machine.specs.isEmpty())
        }
}

/** A POSIX machine that runs only the attachment store's scripts, against an in-memory file map. */
private class FakeShell : ProcessLauncher {
    val files = mutableMapOf<String, ByteArray>()
    val specs = mutableListOf<ProcessSpec>()

    override suspend fun which(command: String): String? = null

    override suspend fun launch(spec: ProcessSpec): RunningProcess {
        specs += spec
        return Process(spec)
    }

    private inner class Process(
        val spec: ProcessSpec,
    ) : RunningProcess {
        private val input = StringBuilder()
        private val output = CompletableDeferred<List<String>>()

        override val stdout: Flow<String> = flow { output.await().forEach { emit(it) } }
        override val stderr: Flow<String> = emptyFlow()

        override suspend fun writeLine(line: String) {
            input.append(line).append('\n')
        }

        // The script runs once Octo has written everything, as `sh -c` would by the end of stdin.
        override suspend fun closeStdin() {
            val script = spec.args[1]
            val path = spec.args.last()
            output.complete(
                when {
                    "base64 -d" in script -> {
                        files[path] = Base64.decode(input.filterNot { it.isWhitespace() })
                        emptyList()
                    }

                    "base64 <" in script -> {
                        listOf(Base64.encode(files.getValue(path)))
                    }

                    else -> {
                        files.keys.removeAll { it == path || it.startsWith("$path/") }
                        emptyList()
                    }
                },
            )
        }

        override suspend fun awaitExit(): Int = output.await().let { 0 }

        override fun kill() = Unit
    }
}
