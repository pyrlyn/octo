package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.IOException
import octo.core.config.OctoConfig
import octo.core.flags.CoreFlags
import octo.core.git.Git
import octo.core.git.GitHubCli
import octo.core.git.GitWorkspace
import octo.core.io.AtomicFile
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
) {
    val core: OctoCore get() = orchestrator

    /** Stops every agent and writes the last state. */
    suspend fun close() = orchestrator.close()

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
            val orchestrator =
                Orchestrator.open(
                    registry,
                    GitWorkspace(Git(launcher)),
                    StateStore(paths.state),
                    terms,
                    paths.worktrees,
                    scope,
                )
            val publisher =
                if (flags.isEnabled(
                        CoreFlags.GIT_PUBLISH,
                    )
                ) {
                    GitTaskPublisher(orchestrator.state, Git(launcher), GitHubCli(launcher))
                } else {
                    null
                }
            return Octo(orchestrator, RegistryAgentCatalog(registry, terms), listOfNotNull(warning), publisher)
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
