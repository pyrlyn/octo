package octo.core.config

import octo.core.remote.shellQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MachineConfigTest {
    @Test
    fun machinesLoadFromConfigAndOldFilesStillParse() {
        val parsed =
            OctoConfig.parse(
                """{"flags":{"remote-ssh":true},"machines":[{"id":"box","host":"me@box.local","port":2222}]}""",
            )
        assertNull(parsed.warning)
        assertEquals(listOf(MachineConfig("box", "me@box.local", 2222)), parsed.config.machines)
        assertEquals(emptyList(), OctoConfig.parse("""{"flags":{}}""").config.machines)
    }

    @Test
    fun worktreeSetupLoadsFromConfig() {
        val parsed = OctoConfig.parse("""{"worktreeSetup":{"/src/app":"npm ci && cp ../.env ."}}""")
        assertEquals(mapOf("/src/app" to "npm ci && cp ../.env ."), parsed.config.worktreeSetup)
        assertEquals(emptyMap(), OctoConfig.parse("""{"flags":{}}""").config.worktreeSetup)
    }

    @Test
    fun agentLimitsComeFromTheConfigAndBadOnesAreWarnedAbout() {
        val config =
            OctoConfig
                .parse(
                    """{"maxRunningAgents":2,"machines":[{"id":"box","host":"box","maxRunningAgents":0},""" +
                        """{"id":"pi","host":"pi","maxRunningAgents":1},{"id":"nas","host":"nas"}]}""",
                ).config

        val limits = config.agentLimits()

        assertEquals(mapOf(null to 2, "pi" to 1), limits.limits)
        assertEquals(listOf("Ignoring maxRunningAgents 0 for machine 'box': it must be 1 or more"), limits.warnings)
        assertEquals(AgentLimits(), OctoConfig.parse("""{"flags":{}}""").config.agentLimits())
    }

    @Test
    fun badEntriesAreCaughtBeforeTheyReachSsh() {
        assertNull(MachineConfig("box-2", "box", worktrees = "/srv/octo").problem())
        assertNotNull(MachineConfig("Box", "box").problem())
        assertNotNull(MachineConfig("box", "-oProxyCommand=id").problem())
        assertNotNull(MachineConfig("box", "box 2").problem())
        assertNotNull(MachineConfig("box", "box", port = 70_000).problem())
        assertNotNull(MachineConfig("box", "box", worktrees = "octo").problem())
    }

    @Test
    fun quotingClosesAndReopensAroundAQuote() {
        assertEquals("'it'\\''s'", shellQuote("it's"))
        assertEquals("''", shellQuote(""))
    }
}
