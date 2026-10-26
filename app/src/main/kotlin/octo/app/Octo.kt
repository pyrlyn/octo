package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.IOException
import octo.core.config.OctoConfig
import octo.core.flags.CoreFlags
import octo.core.git.Git
import octo.core.git.GitHubCli
import octo.core.git.GitWorkspace
import octo.core.io.AtomicFile
import octo.core.messaging.AgentMessaging
import octo.core.messaging.HttpMcpServer
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.OctoCore
import octo.core.orchestrator.Orchestrator
import octo.core.orchestrator.RegistryAgentCatalog
import octo.core.paths.OctoPaths
import octo.core.persist.StateStore
import octo.core.process.ProcessLauncher
import octo.core.publish.GitTaskPublisher
import octo.core.publish.TaskPublisher
import octo.core.terms.TermsGate

/** Everything the window needs, built from one Octo home. */
class Octo private constructor(
    private val orchestrator: Orchestrator,
    val catalog: AgentCatalog,
    /** Start-up problems the core does not know about, such as a broken config file. */
    val warnings: List<String>,
    /** Null unless the `git-publish` flag is on. */
    val publisher: TaskPublisher?,
    /** Null unless the `agent-messaging` flag is on and the server could start. */
    internal val mcpServer: HttpMcpServer?,
) {
    val core: OctoCore get() = orchestrator

    /** Stops every agent and writes the last state. */
    suspend fun close() {
        orchestrator.close()
        mcpServer?.close()
    }

    companion object {
        suspend fun open(
            paths: OctoPaths,
            launcher: ProcessLauncher,
            scope: CoroutineScope,
        ): Octo {
            val (config, warning) = readConfig(AtomicFile(paths.config))
            val flags = config.featureFlags()
            val registry = adapterRegistry(launcher, flags)
            val terms = TermsGate(AtomicFile(paths.acknowledgements))
            val remote =
                if (flags.isEnabled(CoreFlags.REMOTE_SSH)) {
                    remoteMachines(config.machines, launcher, flags)
                } else {
                    RemoteMachines(emptyMap(), emptyList())
                }
            val (messaging, mcpServer, mcpWarning) =
                if (flags.isEnabled(CoreFlags.AGENT_MESSAGING)) startMessaging(scope) else Messaging()
            val orchestrator =
                Orchestrator.open(
                    registry,
                    GitWorkspace(Git(launcher)),
                    StateStore(paths.state),
                    terms,
                    paths.worktrees,
                    scope,
                    machines = remote.machines,
                    mcp = messaging,
                )
            messaging?.attach(orchestrator)
            val publisher =
                if (flags.isEnabled(
                        CoreFlags.GIT_PUBLISH,
                    )
                ) {
                    GitTaskPublisher(orchestrator.state, Git(launcher), GitHubCli(launcher))
                } else {
                    null
                }
            val catalog = RegistryAgentCatalog(registry, terms, remote.machines.mapValues { it.value.registry })
            val warnings = listOfNotNull(warning, mcpWarning) + remote.warnings
            return Octo(orchestrator, catalog, warnings, publisher, mcpServer)
        }

        private data class Messaging(
            val messaging: AgentMessaging? = null,
            val server: HttpMcpServer? = null,
            val warning: String? = null,
        )

        // Without its server agents simply get no messaging tools; the rest of Octo works as before.
        private fun startMessaging(scope: CoroutineScope): Messaging {
            val messaging = AgentMessaging(scope)
            val server =
                try {
                    HttpMcpServer(messaging).start()
                } catch (e: java.io.IOException) {
                    return Messaging(warning = "Agent messaging is off: ${e.message}")
                }
            messaging.bind(server.url)
            return Messaging(messaging, server)
        }

        // An unreadable config must not keep Octo from starting; it runs with defaults and says so.
        private suspend fun readConfig(file: AtomicFile) =
            try {
                OctoConfig.parse(file.read()).let { it.config to it.warning }
            } catch (e: IOException) {
                OctoConfig() to "Could not read ${file.path}: ${e.message}"
            }
    }
}
