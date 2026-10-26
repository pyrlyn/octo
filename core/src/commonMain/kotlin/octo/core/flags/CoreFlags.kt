package octo.core.flags

/** Optional features owned by the core; every one is off unless `config.json` turns it on. */
object CoreFlags {
    val GIT_PUBLISH = FeatureFlag("git-publish", "Commit, push and open a pull request from a task")
    val AGENT_MESSAGING =
        FeatureFlag("agent-messaging", "Let agents message and wait on each other through Octo's MCP server")
    val REMOTE_SSH = FeatureFlag("remote-ssh", "Run tasks on the machines listed in config.json over SSH")
}
