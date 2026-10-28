package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.IOException
import octo.core.access.Devices
import octo.core.access.JvmSecrets
import octo.core.access.RemoteApi
import octo.core.access.RemoteHttpServer
import octo.core.agent.AdapterRegistry
import octo.core.config.AgentLimits
import octo.core.config.OctoConfig
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlag
import octo.core.flags.FeatureFlags
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
import octo.core.plugins.LoadedPlugins
import octo.core.plugins.Plugin
import octo.core.plugins.PluginLoader
import octo.core.preset.FilePresetLibrary
import octo.core.preset.PresetLibrary
import octo.core.process.JvmProcessMonitor
import octo.core.process.ProcessLauncher
import octo.core.publish.GitHubMergedBranches
import octo.core.publish.GitTaskPublisher
import octo.core.publish.SettleMerged
import octo.core.publish.TaskPublisher
import octo.core.terms.TermsGate
import octo.core.triggers.KrontabCronParser
import octo.core.triggers.Triggers
import octo.core.triggers.Webhooks
import octo.core.usage.UsageLog
import octo.core.usage.UsageReport
import wasm.chasm.ChasmRuntime

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
    /** Empty unless the `plugins` flag is on. */
    val plugins: List<Plugin>,
    private val remote: RemoteMachines,
    private val flags: FeatureFlags,
    /** Null unless the `usage` flag is on. */
    val usage: UsageReport?,
    /** Null unless the `triggers` flag is on. */
    private val triggers: Triggers?,
) {
    val core: OctoCore get() = orchestrator

    /** Whether the `handoff` flag lets a task switch its agent or model. */
    val handoff: Boolean get() = flags.isEnabled(CoreFlags.HANDOFF)

    /** Whether the `fork` flag lets a task be forked. */
    val fork: Boolean get() = flags.isEnabled(CoreFlags.FORK)

    /** Whether the `compare` flag lets one prompt go to several agents. */
    val compare: Boolean get() = flags.isEnabled(CoreFlags.COMPARE)

    /** Whether the `sections` flag lets tasks be pinned, snoozed and settled. */
    val sections: Boolean get() = flags.isEnabled(CoreFlags.SECTIONS)

    /** Whether the `palette` flag binds Cmd+K to the command palette. */
    val palette: Boolean get() = flags.isEnabled(CoreFlags.PALETTE)

    /** Whether the `checkpoints` flag lets a task be reverted to an earlier turn. */
    val checkpoints: Boolean get() = flags.isEnabled(CoreFlags.CHECKPOINTS)

    /** Whether the `move-task` flag lets a task move between machines; they come with `remote-ssh`. */
    val moveTask: Boolean get() = flags.isEnabled(CoreFlags.MOVE_TASK) && flags.isEnabled(CoreFlags.REMOTE_SSH)

    /** Whether the `previews` flag lets the diff view preview images, notebooks and other files. */
    val previews: Boolean get() = flags.isEnabled(CoreFlags.PREVIEWS)

    /** Where the remote API listens, e.g. `http://127.0.0.1:47470`. */
    val remoteUrl: String? get() = remoteServer?.url

    /** Stops every agent and writes the last state. */
    suspend fun close() {
        triggers?.close()
        remoteServer?.close()
        orchestrator.close()
        remote.close()
        mcpServer?.close()
    }

    companion object {
        suspend fun open(
            paths: OctoPaths,
            launcher: ProcessLauncher,
            scope: CoroutineScope,
            /** Where agents keep their own history; the default reads none. */
            homes: AgentHomes = AgentHomes(),
        ): Octo {
            val (config, warning) = readConfig(AtomicFile(paths.config))
            val flags = config.featureFlags()
            val plugins = loadPlugins(flags, paths, launcher)
            val registry = adapterRegistry(launcher, flags, plugins.agents.adapters, homes)
            val terms = TermsGate(AtomicFile(paths.acknowledgements))
            val remote =
                flags.ifOn(
                    CoreFlags.REMOTE_SSH,
                ) { remoteMachines(config.machines, launcher, flags, paths.sockets, scope) }
                    ?: RemoteMachines(emptyMap(), emptyList())
            val limits = flags.ifOn(CoreFlags.QUEUE) { config.agentLimits() } ?: AgentLimits()
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
                    processes = JvmProcessMonitor(launcher),
                    defaultPermissionMode = config.defaultPermissionMode,
                    worktreeSetup = config.worktreeSetup,
                    checkpoints = flags.isEnabled(CoreFlags.CHECKPOINTS),
                    compactIdle = flags.isEnabled(CoreFlags.COMPACT_IDLE),
                    agentLimits = limits.limits,
                )
            messaging?.attach(orchestrator)
            val publisher = publishing(flags, orchestrator, launcher, scope)
            val catalog = RegistryAgentCatalog(registry, terms, remote.machines.mapValues { it.value.registry })
            val presets = flags.ifOn(CoreFlags.PRESETS) { FilePresetLibrary.open(AtomicFile(paths.presets)) }
            val usage = flags.ifOn(CoreFlags.USAGE) { usage(paths, orchestrator, registry, scope) }
            val triggers = flags.ifOn(CoreFlags.TRIGGERS) { triggers(config, flags, paths, orchestrator, scope) }
            val access =
                flags.ifOn(CoreFlags.REMOTE_ACCESS) { startRemoteAccess(config, paths, orchestrator, triggers?.first) }
                    ?: RemoteAccess()
            val warnings =
                listOfNotNull(warning, mcpWarning, presets?.warning, access.warning, usage?.second) + remote.warnings +
                    plugins.loaded.warnings + plugins.agents.warnings + triggers?.second.orEmpty() +
                    limits.warnings
            return Octo(
                orchestrator,
                catalog,
                warnings,
                publisher,
                mcpServer,
                presets,
                access.server,
                access.devices,
                plugins.loaded.plugins,
                remote,
                flags,
                usage?.first,
                triggers?.first,
            )
        }

        /** The triggers and their start-up warnings. */
        private suspend fun triggers(
            config: OctoConfig,
            flags: FeatureFlags,
            paths: OctoPaths,
            orchestrator: Orchestrator,
            scope: CoroutineScope,
        ): Pair<Triggers, List<String>> {
            val triggers =
                Triggers.open(
                    config.triggers,
                    orchestrator,
                    AtomicFile(paths.triggers),
                    KrontabCronParser(),
                    JvmSecrets,
                    scope,
                )
            val unreachable =
                "Webhook triggers need the ${CoreFlags.REMOTE_ACCESS.id} flag, which is off".takeIf {
                    config.triggers.any { it.webhook != null } && !flags.isEnabled(CoreFlags.REMOTE_ACCESS)
                }
            return triggers to triggers.warnings + listOfNotNull(unreachable)
        }

        /** The report and, when `usage.json` cannot be read, why nothing is recorded. */
        private suspend fun usage(
            paths: OctoPaths,
            orchestrator: Orchestrator,
            registry: AdapterRegistry,
            scope: CoroutineScope,
        ): Pair<UsageReport, String?> {
            val log = UsageLog.open(AtomicFile(paths.usage))
            log.watch(orchestrator.state, scope)
            val histories = registry.adapters.mapNotNull { adapter -> adapter.usageHistory?.let { adapter.id to it } }
            val names = registry.adapters.associate { it.id to it.displayName }
            return UsageReport(log, histories.toMap(), names) to log.warning
        }

        /**
         * Commit, push and pull request, plus settling tasks whose pull requests merged; both
         * drive the user's gh, so settling also waits for `git-publish`.
         */
        private fun publishing(
            flags: FeatureFlags,
            orchestrator: Orchestrator,
            launcher: ProcessLauncher,
            scope: CoroutineScope,
        ): TaskPublisher? {
            if (!flags.isEnabled(CoreFlags.GIT_PUBLISH)) return null
            val gh = GitHubCli(launcher)
            if (flags.isEnabled(CoreFlags.SECTIONS)) SettleMerged(orchestrator, GitHubMergedBranches(gh)).watch(scope)
            return GitTaskPublisher(orchestrator.state, Git(launcher), gh)
        }

        private inline fun <T> FeatureFlags.ifOn(
            flag: FeatureFlag,
            make: () -> T,
        ): T? = if (isEnabled(flag)) make() else null

        private data class Plugins(
            val loaded: LoadedPlugins,
            val agents: PluginAdapters,
        )

        // Plugin agents run on this computer only: a remote machine may not have their command.
        private suspend fun loadPlugins(
            flags: FeatureFlags,
            paths: OctoPaths,
            launcher: ProcessLauncher,
        ): Plugins {
            val loaded = PluginLoader.load(flags, paths.plugins, ChasmRuntime)
            val taken = builtInAdapters(launcher).map { it.id }.toSet()
            return Plugins(loaded, pluginAdapters(loaded.plugins.map { it.manifest }, launcher, taken))
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
            hooks: Webhooks?,
        ): RemoteAccess {
            val port = config.remoteAccess.port
            val devices = Devices.open(AtomicFile(paths.devices), JvmSecrets)
            val problem = config.remoteAccess.problem() ?: devices.warning
            if (problem != null) return RemoteAccess(warning = "Remote access is off: $problem")
            return try {
                RemoteAccess(RemoteHttpServer(RemoteApi(core, devices, hooks), port).start(), devices)
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
