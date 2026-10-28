package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.access.Proposal
import octo.core.access.ProposalStatus
import octo.core.access.Shares
import octo.core.task.TaskId

data class ShareUiState(
    val open: Boolean = false,
    val shared: Boolean = false,
    /** Shown once, right after it is made: only a hash of its token is kept. */
    val link: String? = null,
    /** The guest's messages waiting for the owner, oldest first. */
    val waiting: List<Proposal> = emptyList(),
    val error: String? = null,
)

/** A task's guest link and the messages its guest proposes. */
class ShareViewModel(
    private val shares: Shares,
    private val taskId: TaskId,
    /** Where links point, e.g. `https://box.tailnet.ts.net`. */
    private val base: String,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = mutable.asStateFlow()

    init {
        scope.launch {
            combine(shares.shares, shares.proposals) { all, proposals ->
                all.any { it.taskId == taskId } to
                    proposals.filter { it.taskId == taskId && it.status == ProposalStatus.Pending }
            }.collect { (shared, waiting) -> mutable.update { it.copy(shared = shared, waiting = waiting) } }
        }
    }

    fun open() = mutable.update { it.copy(open = true, error = null) }

    /** Forgets the link on screen; it keeps working until revoked. */
    fun dismiss() = mutable.update { it.copy(open = false, link = null, error = null) }

    fun create() =
        act {
            val token = shares.share(taskId)
            mutable.update { it.copy(link = "$base${Shares.SHARE_PATH}#$token") }
        }

    fun revoke() =
        act {
            shares.revoke(taskId)
            mutable.update { it.copy(link = null) }
        }

    fun approve(id: String) = act { shares.approve(id) }

    fun reject(id: String) = act { shares.reject(id) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun act(block: suspend () -> Unit) {
        mutable.update { it.copy(error = null) }
        scope.action({ e -> mutable.update { it.copy(error = e.userMessage) } }, block)
    }
}
