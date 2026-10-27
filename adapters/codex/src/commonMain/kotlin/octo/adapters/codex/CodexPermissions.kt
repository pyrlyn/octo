package octo.adapters.codex

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import octo.core.agent.PermissionMode

/** One [PermissionMode] in Codex's terms: who approves what, and what the sandbox lets through. */
internal data class CodexPermissions(
    val approvalPolicy: String,
    val approvalsReviewer: String,
    val sandbox: String,
) {
    /** The fields `thread/start` and `thread/resume` take. */
    fun putThread(builder: JsonObjectBuilder) {
        builder.put("approvalPolicy", approvalPolicy)
        builder.put("approvalsReviewer", approvalsReviewer)
        builder.put("sandbox", sandbox)
    }

    /** The fields `turn/start` takes; they stay in force for the turns after it. */
    fun putTurn(builder: JsonObjectBuilder) {
        builder.put("approvalPolicy", approvalPolicy)
        builder.put("approvalsReviewer", approvalsReviewer)
        builder.put("sandboxPolicy", sandboxPolicy())
    }

    // `turn/start` takes the policy object where `thread/start` takes the mode's name.
    private fun sandboxPolicy(): JsonObject =
        buildJsonObject {
            when (sandbox) {
                READ_ONLY -> {
                    put("type", "readOnly")
                    put("networkAccess", false)
                }

                WORKSPACE_WRITE -> {
                    put("type", "workspaceWrite")
                    putJsonArray("writableRoots") {}
                    put("networkAccess", false)
                    put("excludeTmpdirEnvVar", false)
                    put("excludeSlashTmp", false)
                }

                else -> {
                    put("type", "dangerFullAccess")
                }
            }
        }

    companion object {
        private const val READ_ONLY = "read-only"
        private const val WORKSPACE_WRITE = "workspace-write"

        fun of(mode: PermissionMode): CodexPermissions =
            when (mode) {
                // `untrusted` asks before anything but known-safe reads; a read-only sandbox makes
                // every edit an approval too.
                PermissionMode.Supervised -> CodexPermissions("untrusted", "user", READ_ONLY)

                PermissionMode.AutoAcceptEdits -> CodexPermissions("untrusted", "user", WORKSPACE_WRITE)

                PermissionMode.Auto -> CodexPermissions("on-request", "auto_review", WORKSPACE_WRITE)

                PermissionMode.FullAccess -> CodexPermissions("never", "user", "danger-full-access")
            }
    }
}
