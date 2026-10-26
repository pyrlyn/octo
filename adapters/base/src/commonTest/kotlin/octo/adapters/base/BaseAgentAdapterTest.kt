package octo.adapters.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.AgentNotInstalledException
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.LogStream
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.RunningProcess
import octo.testing.FakeProcess
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseAgentAdapterTest {
    /** Echoes every stdout line as an assistant message. */
    private class EchoAdapter(
        launcher: ProcessLauncher,
        context: CoroutineContext,
    ) : BaseAgentAdapter(launcher, context) {
        override val id = AgentId("echo")
        override val displayName = "Echo"
        override val capabilities = setOf(Capability.Cancel)
        override val binary = "echo-agent"
        override val verifiedVersion = "1.2.3"

        override fun buildArgs(spec: SessionSpec) = listOf("--stdio")

        override fun buildEnv(spec: SessionSpec) = spec.env + ("ECHO_HOME" to "/tmp/echo")

        override fun createSession(
            process: RunningProcess,
            spec: SessionSpec,
        ): ProcessAgentSession =
            object : ProcessAgentSession(process, context) {
                override suspend fun initialize(spec: SessionSpec) {
                    process.writeLine("hello")
                }

                override suspend fun readOutput(lines: Flow<String>) {
                    lines.collect { emit(AgentEvent.Message(Role.Assistant, it)) }
                }

                override suspend fun prompt(text: String) = process.writeLine(text)

                override suspend fun respond(
                    requestId: String,
                    response: RequestResponse,
                ) = Unit

                override suspend fun cancel() = Unit
            }
    }

    private fun TestScope.adapter(
        binaries: Map<String, String> = mapOf("echo-agent" to "/bin/echo-agent"),
        script: suspend FakeProcess.() -> Unit,
    ): Pair<EchoAdapter, FakeProcessLauncher> {
        val launcher = FakeProcessLauncher(backgroundScope, binaries, script)
        return EchoAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun probeReportsMissingBinary() =
        runTest {
            val (adapter, _) = adapter(binaries = emptyMap()) {}
            val probe = adapter.probe()
            assertFalse(probe.installed)
            assertNull(probe.version)
        }

    @Test
    fun probeFlagsVersionDrift() =
        runTest {
            val (adapter, launcher) = adapter { emit("echo-agent 1.3.0 (build 7)") }
            val probe = adapter.probe()
            assertEquals("1.3.0", probe.version)
            assertTrue(probe.versionDrift)
            assertEquals(AuthState.Unknown, probe.auth)
            assertEquals(listOf("--version"), launcher.launched.single().args)
        }

    @Test
    fun startFailsWhenNotInstalled() =
        runTest {
            val (adapter, _) = adapter(binaries = emptyMap()) {}
            assertFailsWith<AgentNotInstalledException> { adapter.start(SessionSpec("/repo")) }
        }

    @Test
    fun sessionStreamsOutputLogsAndExit() =
        runTest {
            val (adapter, launcher) = adapter { replay(readFixture("src/commonTest/fixtures/echo.transcript")) }
            val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("r"), env = mapOf("A" to "1")))
            session.prompt("hi")
            val events = session.events.toList()

            val spec = launcher.launched.single()
            assertEquals("/bin/echo-agent", spec.command)
            assertEquals(listOf("--stdio"), spec.args)
            assertEquals("/repo", spec.workingDir)
            assertEquals(mapOf("A" to "1", "ECHO_HOME" to "/tmp/echo"), spec.env)
            // stdout and stderr are separate pipes, so only the order within each is fixed.
            assertEquals(
                listOf(AgentEvent.Message(Role.Assistant, "ready"), AgentEvent.Message(Role.Assistant, "you said hi")),
                events.filterIsInstance<AgentEvent.Message>(),
            )
            assertEquals(
                listOf(AgentEvent.Log(LogStream.Stderr, "warming up")),
                events.filterIsInstance<AgentEvent.Log>(),
            )
            assertEquals(AgentEvent.Exited(0), events.last())
            assertEquals(listOf("hello", "hi"), launcher.processes.single().written)
        }

    @Test
    fun closeKillsTheProcess() =
        runTest {
            val (adapter, launcher) = adapter { while (true) readLine() ?: break }
            val session = adapter.start(SessionSpec("/repo"))
            session.close()
            val events = session.events.toList()
            assertTrue(launcher.processes.single().killed)
            assertEquals(AgentEvent.Exited(FakeProcess.KILLED), events.last())
        }
}
