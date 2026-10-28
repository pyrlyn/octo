package octo.core.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile
import octo.core.io.ReadOnlyFileException
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId

/** A task shared by link; only a hash of the link's token is kept, so the file alone lets nobody in. */
@Serializable
data class Share(
    val id: String,
    val taskId: TaskId,
    val tokenHash: String,
)

enum class ProposalStatus { Pending, Approved, Rejected }

/** A message a guest wants sent to the agent; it goes only once the owner approves it. */
data class Proposal(
    val id: String,
    val shareId: String,
    val taskId: TaskId,
    val from: String,
    val text: String,
    val status: ProposalStatus = ProposalStatus.Pending,
)

/**
 * Shared tasks in `shares.json`, one link per task, and their guests' proposals. Proposals live
 * in memory: they wait for the owner, who is at the window now, and a restart clears them.
 */
class Shares private constructor(
    private val file: JsonListFile<Share>,
    private val secrets: Secrets,
    private val core: OctoCore,
) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow<List<Proposal>>(emptyList())

    val shares: StateFlow<List<Share>> = file.items

    val proposals: StateFlow<List<Proposal>> = mutable.asStateFlow()

    /** Set when `shares.json` could not be read; sharing and revoking are refused until it is fixed. */
    val warning: String? get() = file.warning

    /** Shares [taskId] under a new token, which ends any earlier link to it; the token is shown this once. */
    suspend fun share(taskId: TaskId): String {
        warning?.let { throw ReadOnlyFileException(it) }
        val token = hex(secrets.randomBytes(TOKEN_BYTES))
        val share = Share(hex(secrets.randomBytes(ID_BYTES)), taskId, hash(token))
        file.change { all -> all.filter { it.taskId != taskId } + share }
        dropProposals { it.taskId == taskId }
        return token
    }

    /** Locks [taskId]'s guest out at once and drops what they proposed. */
    suspend fun revoke(taskId: TaskId) {
        file.change { all -> all.filter { it.taskId != taskId } }
        dropProposals { it.taskId == taskId }
    }

    fun shared(taskId: TaskId): Boolean = shares.value.any { it.taskId == taskId }

    /** The share [token] opens, or null for a missing, unknown or revoked one. */
    fun share(token: String?): Share? {
        if (token == null) return null
        val hash = hash(token)
        return shares.value.firstOrNull { it.tokenHash == hash }
    }

    suspend fun propose(
        share: Share,
        from: String,
        text: String,
    ): Proposal {
        val name = from.trim().ifEmpty { "Guest" }
        require(name.length <= Devices.MAX_NAME && name.none { it.isISOControl() }) {
            "A name is up to ${Devices.MAX_NAME} printable characters"
        }
        require(text.isNotBlank() && text.length <= MAX_TEXT) { "A message is 1 to $MAX_TEXT characters" }
        return lock.withLock {
            val mine = mutable.value.filter { it.shareId == share.id }
            check(mine.count { it.status == ProposalStatus.Pending } < MAX_PENDING) {
                "$MAX_PENDING messages are already waiting for the owner"
            }
            val proposal = Proposal(hex(secrets.randomBytes(ID_BYTES)), share.id, share.taskId, name, text)
            // A guest's history is capped, so a busy link cannot grow memory without end.
            val dropped = mine.filter { it.status != ProposalStatus.Pending }.dropLast(KEPT - MAX_PENDING).toSet()
            mutable.update { all -> all.filterNot { it in dropped } + proposal }
            proposal
        }
    }

    /** Sends the proposal to the agent as the owner's prompt; a failed send leaves it waiting. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun approve(id: String) {
        val proposal = settle(id, ProposalStatus.Approved)
        try {
            core.sendPrompt(proposal.taskId, proposal.text)
        } catch (e: Exception) {
            mutable.update { all -> all.map { if (it.id == id) proposal else it } }
            throw e
        }
    }

    suspend fun reject(id: String) {
        settle(id, ProposalStatus.Rejected)
    }

    // Settled before the send, so a second click cannot send the same message twice.
    private suspend fun settle(
        id: String,
        status: ProposalStatus,
    ): Proposal =
        lock.withLock {
            val proposal = mutable.value.firstOrNull { it.id == id && it.status == ProposalStatus.Pending }
            checkNotNull(proposal) { "That message was already handled" }
            mutable.update { all -> all.map { if (it.id == id) it.copy(status = status) else it } }
            proposal
        }

    private suspend fun dropProposals(which: (Proposal) -> Boolean) =
        lock.withLock {
            mutable.update { it.filterNot(which) }
        }

    private fun hash(token: String) = hex(secrets.sha256(token.encodeToByteArray()))

    companion object {
        /** Where a guest link points; the token follows in the fragment, e.g. `/share#<token>`. */
        const val SHARE_PATH = "/share"
        const val MAX_TEXT = 4000
        const val MAX_PENDING = 10

        /** Proposals kept per link, waiting or settled. */
        const val KEPT = 50
        private const val TOKEN_BYTES = 32
        private const val ID_BYTES = 8

        suspend fun open(
            file: AtomicFile,
            secrets: Secrets,
            core: OctoCore,
        ) = Shares(JsonListFile.open(file, Share.serializer(), "shares"), secrets, core)

        private fun hex(bytes: ByteArray) = bytes.toHexString()
    }
}
