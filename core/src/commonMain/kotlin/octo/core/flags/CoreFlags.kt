package octo.core.flags

/** Optional features owned by the core; every one is off unless `config.json` turns it on. */
object CoreFlags {
    val GIT_PUBLISH = FeatureFlag("git-publish", "Commit, push and open a pull request from a task")
    val AGENT_MESSAGING =
        FeatureFlag("agent-messaging", "Let agents message and wait on each other through Octo's MCP server")
    val PRESETS = FeatureFlag("presets", "Save teams of agents with roles and prompts and start them together")
    val REMOTE_SSH = FeatureFlag("remote-ssh", "Run tasks on the machines listed in config.json over SSH")
    val REMOTE_ACCESS =
        FeatureFlag("remote-access", "Let paired devices use Octo through a tunnel to its loopback API")
    val PLUGINS = FeatureFlag("plugins", "Load WebAssembly plugins that add agents and task actions")
    val HANDOFF =
        FeatureFlag("handoff", "Switch a task's agent or model and hand the conversation over to the new one")
    val FORK = FeatureFlag("fork", "Fork a task into a new branch that starts from its current state")
    val COMPARE = FeatureFlag("compare", "Send one prompt to several agents or models, each in its own task")
    val SECTIONS = FeatureFlag("sections", "Pin, snooze and settle tasks into sections of the sidebar")
}
