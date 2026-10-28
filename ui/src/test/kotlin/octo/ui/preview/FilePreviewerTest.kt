package octo.ui.preview

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.testing.FakeProcess
import octo.testing.FakeProcessLauncher
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FilePreviewerTest {
    private val worktree: Path = Files.createTempDirectory("octo-preview-test").toRealPath()

    private fun TestScope.previewer(
        binaries: Map<String, String> = emptyMap(),
        maxBytes: Long = FilePreviewer.DEFAULT_MAX_BYTES,
        script: suspend FakeProcess.() -> Unit = {},
    ): Pair<FilePreviewer, FakeProcessLauncher> {
        val launcher = FakeProcessLauncher(backgroundScope, binaries, script)
        return FilePreviewer(launcher, StandardTestDispatcher(testScheduler), maxBytes) to launcher
    }

    private suspend fun TestScope.preview(path: String) = previewer().first.preview(worktree.toString(), path)

    @AfterTest
    fun cleanUp() {
        worktree.toFile().deleteRecursively()
    }

    private fun file(
        path: String,
        bytes: ByteArray,
    ) = worktree.resolve(path).also {
        it.parent.createDirectories()
        it.writeBytes(bytes)
    }

    private fun png(
        width: Int,
        height: Int,
    ): ByteArray =
        Surface
            .makeRasterN32Premul(width, height)
            .makeImageSnapshot()
            .encodeToData(EncodedImageFormat.PNG)!!
            .bytes

    @Test
    fun theKindComesFromTheExtension() =
        runTest {
            val (previewer, _) = previewer()
            assertTrue(previewer.canPreview("docs/Chart.PNG"))
            assertTrue(previewer.canPreview("analysis.ipynb"))
            assertTrue(previewer.canPreview("dist/app.whl"))
            assertFalse(previewer.canPreview("README.md"))
            assertFalse(previewer.canPreview("Makefile"))
            assertFalse(previewer.canPreview("png"))
        }

    @Test
    fun onlyFilesInsideTheWorktreeUnderTheLimitAreRead() =
        runTest {
            val outside = Files.createTempFile("octo-secret", ".png")
            Files.createSymbolicLink(worktree.resolve("link.png"), outside)
            file("big.png", ByteArray(64))
            val (previewer, _) = previewer(maxBytes = 32)
            for (path in listOf("link.png", "../${outside.fileName}", outside.toString(), "big.png", "missing.png")) {
                assertFailsWith<Exception>(path) { previewer.preview(worktree.toString(), path) }
            }
            Files.delete(outside)
        }

    @Test
    fun picturesDecodeAndSvgUsesItsViewBox() =
        runTest {
            file("a.png", png(3, 2))
            file(
                "b.svg",
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 20" width="100%"/>""".toByteArray(),
            )
            val raster = assertIs<Preview.Picture>(preview("a.png"))
            assertEquals(3 to 2, raster.bitmap.width to raster.bitmap.height)
            val svg = assertIs<Preview.Picture>(preview("b.svg"))
            assertEquals(40 to 20, svg.bitmap.width to svg.bitmap.height)
            file("broken.png", "not a picture".toByteArray())
            assertFailsWith<Exception> { preview("broken.png") }
        }

    @Test
    fun aNotebookKeepsCellsAndOutputs() =
        runTest {
            val image = Base64.getEncoder().encodeToString(png(2, 2))
            file(
                "n.ipynb",
                """
                {"nbformat": 4, "cells": [
                  {"cell_type": "markdown", "source": ["# Title\n", "Text"]},
                  {"cell_type": "code", "source": "print(1)", "outputs": [
                    {"output_type": "stream", "name": "stdout", "text": ["1\n"]},
                    {"output_type": "execute_result", "data": {"text/plain": "2"}},
                    {"output_type": "display_data", "data": {"image/png": "$image", "text/plain": "<Figure>"}},
                    {"output_type": "display_data", "data": {"image/png": "AAAA", "text/plain": "<Broken>"}},
                    {"output_type": "error", "ename": "ValueError", "evalue": "bad", "traceback": []}
                  ]}
                ]}
                """.trimIndent().toByteArray(),
            )
            val notebook = assertIs<Preview.Notebook>(preview("n.ipynb"))
            assertEquals(listOf("markdown", "code"), notebook.cells.map { it.kind })
            assertEquals("# Title\nText", notebook.cells[0].source)
            val outputs = notebook.cells[1].outputs
            assertEquals(Output.Text("1\n"), outputs[0])
            assertEquals(Output.Text("2"), outputs[1])
            assertIs<Output.Image>(outputs[2])
            assertEquals(Output.Text("<Broken>"), outputs[3], "a broken picture falls back to its text")
            assertEquals(Output.Text("ValueError: bad", error = true), outputs[4])
            file("plain.ipynb", "[]".toByteArray())
            assertFailsWith<Exception> { preview("plain.ipynb") }
        }

    @Test
    fun anArchiveListsItsEntries() =
        runTest {
            ZipOutputStream(worktree.resolve("a.zip").outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("dir/"))
                zip.putNextEntry(ZipEntry("dir/a.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
            val archive = assertIs<Preview.Archive>(preview("a.zip"))
            assertEquals(listOf(Entry("dir/", 0), Entry("dir/a.txt", 5)), archive.entries)
            assertEquals(2, archive.total)
            file("fake.zip", "not a zip".toByteArray())
            assertFailsWith<Exception> { preview("fake.zip") }
        }

    @Test
    fun aDatabaseIsReadThroughSqliteReadOnlyAndSafe() =
        runTest {
            file("app.db", "SQLite format 3\u0000".toByteArray() + ByteArray(84))
            val (previewer, launcher) =
                previewer(mapOf("sqlite3" to "/bin/sqlite3")) {
                    val sql = spec.args.last()
                    when {
                        "sqlite_master" in sql -> {
                            emit("""[{"name":"users"},{"name":"we\"ird"}]""")
                        }

                        "\"users\"" in sql -> {
                            emit(
                                """[{"id":1,"name":"Ann","bio":null},{"id":2,"name":"Bo","bio":"x"}]""",
                            )
                        }
                    }
                }
            val db = assertIs<Preview.Database>(previewer.preview(worktree.toString(), "app.db"))
            assertEquals(
                listOf(
                    Table(
                        "users",
                        listOf("id", "name", "bio"),
                        listOf(listOf("1", "Ann", "NULL"), listOf("2", "Bo", "x")),
                    ),
                    Table("we\"ird", emptyList(), emptyList()),
                ),
                db.tables,
            )
            val specs = launcher.launched
            assertTrue(specs.all { it.command == "/bin/sqlite3" && it.args.containsAll(listOf("-readonly", "-safe")) })
            assertEquals("SELECT * FROM \"we\"\"ird\" LIMIT 50", specs.last().args.last(), "the name is quoted")

            file("notes.db", "plain text".toByteArray())
            assertFailsWith<IllegalArgumentException> { previewer.preview(worktree.toString(), "notes.db") }
            val (missing, _) = previewer()
            assertFailsWith<IllegalStateException> { missing.preview(worktree.toString(), "app.db") }
        }

    @Test
    fun mermaidDrawsThroughMmdcOrShowsItsSource() =
        runTest {
            val diagram = "graph TD; A-->B"
            worktree.resolve("flow.mmd").writeText(diagram)
            val source = assertIs<Preview.Source>(preview("flow.mmd"))
            assertEquals(diagram, source.text)
            assertTrue("mmdc" in source.note)

            val picture = png(5, 4)
            val (drawing, launcher) =
                previewer(mapOf("mmdc" to "/bin/mmdc")) {
                    Path(spec.args[spec.args.indexOf("-o") + 1]).writeBytes(picture)
                }
            val drawn = assertIs<Preview.Picture>(drawing.preview(worktree.toString(), "flow.mmd"))
            assertEquals(5 to 4, drawn.bitmap.width to drawn.bitmap.height)
            assertEquals(worktree.resolve("flow.mmd").toString(), launcher.launched.single().args[1])

            val (failing, _) =
                previewer(mapOf("mmdc" to "/bin/mmdc")) {
                    emitError("Parse error on line 1")
                    exit(1)
                }
            val failed = assertIs<Preview.Source>(failing.preview(worktree.toString(), "flow.mmd"))
            assertTrue("Parse error on line 1" in failed.note)
        }
}
