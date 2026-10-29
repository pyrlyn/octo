package octo.feature.common

import octo.core.agent.PermissionMode

/** What each mode lets the agent do, in the words settings and onboarding both show. */
val PermissionMode.description: String
    get() =
        when (this) {
            PermissionMode.Supervised -> "Asks before commands and file changes."
            PermissionMode.AutoAcceptEdits -> "Applies file edits without asking; other actions may still ask."
            PermissionMode.Auto -> "The agent's own review approves routine actions and asks about the rest."
            PermissionMode.FullAccess -> "No prompts. Use only in throwaway worktrees."
        }
