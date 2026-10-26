package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.IOException
import octo.core.access.Devices
import octo.core.access.JvmSecrets
import octo.core.access.RemoteApi
import octo.core.access.RemoteHttpServer
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
import octo.core.preset.FilePresetLibrary
import octo.core.preset.PresetLibrary
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
    /** Null unless the `presets` flag is on. */
    val presets: PresetLibrary?,
    /** Null unless the `remote-access` flag is on and its server could start. */
    internal val remoteServer: RemoteHttpServer?,
    /** Paired devices; null exactly when [remoteServer] is. */
    val devices: Devices?,
) {
    val core: OctoCore get() = orchestrator

    /** Where the remote API listens, e.g. `http://127.0.0.1:47470`. */
    val remoteUrl: String? get() = remoteServer?.url

    /** Stops every agent and writes the last state. */
    suspend fun close() {
        remoteServer?.close()
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
            val presets =
                if (flags.isEnabled(
                        CoreFlags.PRESETS,
                    )
                ) {
                    FilePresetLibrary.open(AtomicFile(paths.presets))
                } else {
                    null
                }
            val access =
                if (flags.isEnabled(CoreFlags.REMOTE_ACCESS)) {
                    startRemoteAccess(config, paths, orchestrator)
                } else {
                    RemoteAccess()
                }
            val warnings =
                listOfNotNull(warning, mcpWarning, presets?.warning, access.warning) + remote.warnings
            return Octo(orchestrator, catalog, warnings, publisher, mcpServer, presets, access.server, access.devices)
        }

        private data class RemoteAccess(
            val server: RemoteHttpServer? = null,
            val devices: Devices? = null,
            val warning: String? = null,
        )

        // A taken port or a broken devices.json turns remote access off; the window works as before.
        private suspend fun startRemoteAccess(
            config: OctoConfig,
            paths: OctoPaths,
            core: OctoCore,
        ): RemoteAccess {
            val port = config.remoteAccess.port
            val devices = Devices.open(AtomicFile(paths.devices), JvmSecrets)
            val problem = config.remoteAccess.problem() ?: devices.warning
            if (problem != null) return RemoteAccess(warning = "Remote access is off: $problem")
            return try {
                RemoteAccess(RemoteHttpServer(RemoteApi(core, devices), port).start(), devices)
            } catch (e: java.io.IOException) {
                RemoteAccess(warning = "Remote access is off: port $port: ${e.message}")
            }
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
