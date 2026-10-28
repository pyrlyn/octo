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
    val USAGE = FeatureFlag("usage", "Show token use per agent and model, and when an agent hits its usage limit")
    val PALETTE = FeatureFlag("palette", "Find tasks and run actions from a Cmd+K command palette")
    val CHECKPOINTS =
        FeatureFlag("checkpoints", "Snapshot the worktree after every turn and revert a task to a snapshot")
    val COMPACT_IDLE =
        FeatureFlag("compact-idle", "Compact a large idle conversation just before its prompt cache expires")
    val TRIGGERS =
        FeatureFlag("triggers", "Start the tasks in config.json's triggers on a schedule or from a webhook")
    val QUEUE =
        FeatureFlag("queue", "Cap the live agents per machine at maxRunningAgents; later tasks wait their turn")
    val MOVE_TASK =
        FeatureFlag("move-task", "Move a task with its uncommitted work to another machine; needs remote-ssh")
    val GUEST_SHARE =
        FeatureFlag("guest-share", "Let a guest watch a task by link and propose messages; needs remote-access")
    val PREVIEWS =
        FeatureFlag("previews", "Preview images, notebooks, archives, SQLite and Mermaid files in the diff view")
    val NOTIFICATIONS =
        FeatureFlag("notifications", "Show an OS notification when a task blocks, fails or finishes out of sight")
}
