package octo.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import octo.core.access.ApiJson
import octo.core.access.Devices
import octo.core.access.JvmSecrets
import octo.core.access.RemoteApi
import octo.core.access.RemoteHttpServer
import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.io.AtomicFile
import octo.core.paths.HostOs
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.testing.FakeOctoCore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs `octo` against the real loopback server over a fake core; its files live in a temp dir. */
class OctoTest {
    private val home = Files.createTempDirectory("octo-cli").toString()
    private val core = FakeOctoCore()
    private val devices = runBlocking { Devices.open(AtomicFile(Path(home, "devices.json")), JvmSecrets) }
    private val server = RemoteHttpServer(RemoteApi(core, devices), 0).start()
    private val t9 = TaskId("t9")
    private var clock = 0L

    @AfterTest
    fun close() = server.close()

    private fun octo(
        args: String,
        env: Map<String, String> = mapOf("OCTO_HOME" to home, "OCTO_URL" to server.url),
        onSleep: () -> Unit = {},
    ): CliktCommandTestResult =
        Octo(
            env,
            userHome = "$home/not-home",
            cwd = "/src",
            os = HostOs.Linux,
            sleep = {
                clock += it
                onSleep()
            },
            now = { clock },
        ).test(args)

    private fun pair() {
        val code = runBlocking { devices.startPairing() }.code
        assertEquals(0, octo("pair $code").statusCode)
    }

    private fun status(status: AgentStatus) =
        core.put(
            TaskView(
                FakeOctoCore.task(t9),
                status,
                failure =
                    "boom".takeIf {
                        status ==
                            AgentStatus.Failed
                    },
            ),
        )

    @Test
    fun pairingKeepsAPrivateTokenForTheOtherCommands() {
        val unpaired = octo("task list")
        assertEquals(1, unpaired.statusCode)
        assertTrue("Not paired with Octo at ${server.url}" in unpaired.stderr, unpaired.stderr)

        val wrong = octo("pair WRONGONE")
        assertEquals(1, wrong.statusCode)
        assertFalse(File(home, "cli.json").exists())

        pair()
        val file = File(home, "cli.json").toPath()
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        assertEquals(1, devices.devices.value.size)
        assertEquals(0, octo("task list").statusCode)
        assertFalse(File(home, "not-home").exists(), "nothing is written to the user's home")

        val elsewhere = octo("--url http://127.0.0.1:1 task list")
        assertTrue("Not paired with Octo at http://127.0.0.1:1" in elsewhere.stderr, "the token stays with its Octo")
    }

    @Test
    fun listStartSendAndRead() {
        pair()
        status(AgentStatus.Idle)
        assertEquals("t9\tIdle\tdemo\tTask t9\n", octo("task list").stdout)
        val json = ApiJson.parseToJsonElement(octo("task list --json").stdout)
        assertEquals(
            "t9",
            json.jsonObject["tasks"]!!
                .jsonArray
                .single()
                .jsonObject["id"]!!
                .jsonPrimitive.content,
        )

        val started = octo("task start --repo app --base main --agent claude --title Fix --prompt Go --mode auto")
        assertEquals("t1\n", started.stdout, started.stderr)
        assertEquals("create Fix Go as Auto", core.calls.last())
        assertEquals(
            "/src/app",
            core.state.value[TaskId("t1")]!!
                .task.repo,
            "relative to the caller",
        )

        assertEquals(0, octo("task send t9 hello there").statusCode)
        assertEquals("prompt hello there", core.calls.last())
        assertEquals(1, octo("task send nope hi").statusCode)

        core.transcripts.value =
            mapOf(
                t9 to
                    listOf(
                        TranscriptEntry.Text(Role.User, "hello"),
                        TranscriptEntry.Tool("1", "Read a.txt", "read", ToolStatus.Completed),
                        TranscriptEntry.Text(Role.Assistant, "Done"),
                        TranscriptEntry.Notice("Agent exited", error = true),
                    ),
            )
        assertEquals(
            "user: hello\ntool: Read a.txt (completed)\nassistant: Done\nerror: Agent exited\n",
            octo("task read t9").stdout,
        )
        assertTrue("\"entries\"" in octo("task read t9 --json").stdout)
    }

    @Test
    fun waitEndsWithTheTurnAndSaysHowItEnded() {
        pair()
        status(AgentStatus.Working)
        val done = octo("task wait t9") { status(AgentStatus.Idle) }
        assertEquals(0, done.statusCode)
        assertEquals("t9\tIdle\n", done.stdout)

        status(AgentStatus.Working)
        assertEquals(2, octo("task wait t9") { status(AgentStatus.Blocked) }.statusCode)
        status(AgentStatus.Working)
        val failed = octo("task wait t9 --json") { status(AgentStatus.Failed) }
        assertEquals(1, failed.statusCode)
        assertEquals(
            "boom",
            ApiJson
                .parseToJsonElement(failed.stdout)
                .jsonObject["failure"]!!
                .jsonPrimitive.content,
        )

        status(AgentStatus.Working)
        clock = 0
        val slow = octo("task wait t9 --timeout 2.5")
        assertEquals(124, slow.statusCode)
        assertEquals(3_000, clock)

        val unknown = octo("task wait t1")
        assertEquals(1, unknown.statusCode)
        assertTrue("Octo has no task t1" in unknown.stderr)
    }

    @Test
    fun anOctoThatIsNotListeningSaysHowToTurnItOn() {
        val result =
            octo(
                "task list",
                mapOf(
                    "OCTO_HOME" to home,
                    "OCTO_URL" to "http://127.0.0.1:1",
                    "OCTO_TOKEN" to "x",
                ),
            )
        assertEquals(1, result.statusCode)
        assertTrue("Is it running with remote-access on?" in result.stderr, result.stderr)
    }
}
