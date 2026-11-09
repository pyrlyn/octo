package octo.storage

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.access.Device
import octo.core.access.Share
import octo.core.agent.AgentId
import octo.core.agent.AgentSessionRef
import octo.core.agent.Role
import octo.core.io.AtomicFile
import octo.core.io.ReadOnlyFileException
import octo.core.paths.OctoPaths
import octo.core.persist.JsonStateStore
import octo.core.persist.LoadedState
import octo.core.persist.PersistedState
import octo.core.persist.PersistedTask
import octo.core.preset.Preset
import octo.core.preset.PresetMember
import octo.core.status.AgentStatus
import octo.core.task.Layout
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import octo.core.triggers.TriggerRecord
import octo.core.usage.UsageRecord
import octo.testing.tempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageTest {
    private fun paths() = OctoPaths(tempDir("octo-storage"))

    private fun task(
        id: String,
        title: String = id,
    ) = Task(
        id = TaskId(id),
        title = title,
        repo = "/repo",
        worktree = "/wt/$id",
        branch = "octo/$id",
        baseRef = "main",
        agent = AgentId("claude"),
        sessionRef = AgentSessionRef("s-$id"),
        lastStatus = AgentStatus.Working,
    )

    private fun state(vararg tasks: PersistedTask) =
        PersistedState(tasks = tasks.toList(), layout = Layout(selectedTask = TaskId("a"), sidebarWidth = 240f))

    private fun usage(at: Long) =
        UsageRecord(atMs = at, agent = AgentId("codex"), model = "m", inputTokens = at, outputTokens = 2)

    private suspend fun <T> reopen(
        paths: OctoPaths,
        block: suspend (Storage) -> T,
    ): T {
        val storage = Storage.open(paths)
        return try {
            block(storage)
        } finally {
            storage.close()
        }
    }

    @Test
    fun stateSurvivesAReopenAndFollowsEveryChange() =
        runTest {
            val paths = paths()
            val a =
                PersistedTask(task("a"), listOf(TranscriptEntry.Text(Role.User, "hi"), TranscriptEntry.Notice("done")))
            val b = PersistedTask(task("b"), listOf(TranscriptEntry.Thought("hmm")))
            reopen(paths) {
                assertEquals(LoadedState(PersistedState(), emptyList()), it.state.load())
                it.state.save(state(a, b))
            }
            val changed =
                state(
                    b.copy(task = task("b", "renamed")),
                    a.copy(
                        transcript =
                            a.transcript + TranscriptEntry.Notice("again"),
                    ),
                )
            reopen(paths) {
                assertEquals(LoadedState(state(a, b), emptyList()), it.state.load())
                it.state.save(changed)
            }
            reopen(paths) {
                assertEquals(changed, it.state.load().state)
                it.state.save(state(a))
            }
            reopen(paths) { assertEquals(state(a), it.state.load().state) }
        }

    @Test
    fun listsKeepTheirOrderAcrossAReopen() =
        runTest {
            val paths = paths()
            val devices = listOf(Device("d2", "Phone", "h2"), Device("d1", "Laptop", "h1"))
            val shares = listOf(Share("s1", TaskId("a"), "h"))
            val presets =
                listOf(
                    Preset(
                        "Pair",
                        listOf(
                            PresetMember("lead", AgentId("claude"), "Lead"),
                            PresetMember("review", AgentId("codex")),
                        ),
                    ),
                )
            val triggers =
                listOf(TriggerRecord("nightly", lastRun = 42, deliveries = listOf("x", "y")), TriggerRecord("hook"))
            reopen(paths) {
                it.devices().change { devices }
                it.shares().change { shares }
                it.presets().change { presets }
                it.triggerRecords().change { triggers }
            }
            reopen(paths) {
                assertEquals(devices, it.devices().items.value)
                assertEquals(shares, it.shares().items.value)
                assertEquals(presets, it.presets().items.value)
                assertEquals(triggers, it.triggerRecords().items.value)
                it.devices().change { all -> all.drop(1) }
            }
            reopen(paths) { assertEquals(devices.drop(1), it.devices().items.value) }
        }

    @Test
    fun usageKeepsOnlyTheNewestRecords() =
        runTest {
            val paths = paths()
            reopen(paths) {
                val store = it.usage()
                store.append(listOf(usage(1), usage(2)), keep = 3)
                store.append(listOf(usage(3), usage(4)), keep = 3)
                assertEquals(listOf(usage(2), usage(3), usage(4)), store.records.value)
            }
            reopen(paths) { assertEquals(listOf(usage(2), usage(3), usage(4)), it.usage().records.value) }
        }

    @Test
    fun acknowledgementsOnlyGrow() =
        runTest {
            val paths = paths()
            reopen(paths) {
                it.acknowledgements.write(setOf("claude@1"))
                it.acknowledgements.write(setOf("claude@1", "codex@2"))
            }
            reopen(paths) { assertEquals(setOf("claude@1", "codex@2"), it.acknowledgements.read()) }
        }

    @Test
    fun importsTheJsonFilesOnceAndRenamesThem() =
        runTest {
            val paths = paths()
            val a = PersistedTask(task("a"), listOf(TranscriptEntry.Text(Role.User, "hi")))
            JsonStateStore(paths.state).save(state(a))
            write(paths.devices, """{"version": 1, "devices": [{"id": "d1", "name": "Phone", "tokenHash": "h"}]}""")
            write(
                paths.usage,
                """{"version": 1, "records": [{"atMs": 1, "agent": "codex", "inputTokens": 1, "outputTokens": 2}]}""",
            )
            write(paths.acknowledgements, """{"acknowledged": ["claude@1"]}""")
            write(paths.shares, "not json")

            val warnings =
                reopen(paths) {
                    assertEquals(state(a), it.state.load().state)
                    assertEquals(listOf(Device("d1", "Phone", "h")), it.devices().items.value)
                    assertEquals(listOf(usage(1).copy(model = null, inputTokens = 1)), it.usage().records.value)
                    assertEquals(setOf("claude@1"), it.acknowledgements.read())
                    assertEquals(emptyList(), it.shares().items.value)
                    it.warnings
                }
            assertTrue(warnings.single().startsWith("Did not import ${paths.shares}"), warnings.toString())
            for (path in listOf(paths.state, paths.devices, paths.usage, paths.acknowledgements)) {
                assertFalse(SystemFileSystem.exists(path), "$path")
                assertTrue(SystemFileSystem.exists(Path("$path.imported")), "$path.imported")
            }
            assertTrue(SystemFileSystem.exists(paths.shares))

            // A second start finds the import done and leaves the unreadable file alone.
            write(paths.devices, """{"version": 1, "devices": []}""")
            reopen(paths) {
                assertEquals(emptyList(), it.warnings)
                assertEquals(listOf(Device("d1", "Phone", "h")), it.devices().items.value)
            }
        }

    @Test
    fun aDatabaseThatCannotBeOpenedIsMovedAside() =
        runTest {
            val paths = paths()
            write(paths.database, "this is not a SQLite database, only text long enough to look like one")
            reopen(paths) {
                assertTrue(it.warnings.single().contains("moved to ${paths.database}.corrupt"), it.warnings.toString())
                it.devices().change { listOf(Device("d1", "Phone", "h")) }
            }
            assertTrue(SystemFileSystem.exists(Path("${paths.database}.corrupt")))
            reopen(paths) { assertEquals(listOf(Device("d1", "Phone", "h")), it.devices().items.value) }
        }

    @Test
    fun aListThatCannotBeReadRefusesChanges() =
        runTest {
            val storage = Storage.inMemory()
            storage.presets().change { listOf(Preset("Solo", listOf(PresetMember("lead", AgentId("claude"))))) }
            // A members column written by something else no longer decodes as a preset.
            storage.db.records().insertPresets(listOf(PresetRow("Broken", 1, "not json")))
            val presets = storage.presets()
            assertTrue(presets.warning!!.startsWith("Could not read the presets"))
            assertFailsWith<ReadOnlyFileException> { presets.change { emptyList() } }
            storage.close()
        }

    private suspend fun write(
        path: Path,
        text: String,
    ) = AtomicFile(path).write(text)
}
