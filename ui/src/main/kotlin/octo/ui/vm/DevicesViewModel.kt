package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.access.Device
import octo.core.access.Devices

data class DevicesUiState(
    /** Where the API listens on this computer. */
    val url: String,
    val devices: List<Device> = emptyList(),
    /** The open pairing code as the user reads it, e.g. `ABCD-EFGH`. */
    val code: String? = null,
    val validMinutes: Long = 0,
    val error: String? = null,
)

/** Pairs devices with the remote API and revokes them. */
class DevicesViewModel(
    private val devices: Devices,
    url: String,
    private val parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(DevicesUiState(url))
    val state: StateFlow<DevicesUiState> = mutable.asStateFlow()

    init {
        scope.launch { devices.devices.collect { all -> mutable.update { it.copy(devices = all) } } }
        scope.launch {
            devices.pairing.collect { open ->
                mutable.update {
                    it.copy(
                        code = open?.code?.chunked(CODE_GROUP)?.joinToString("-"),
                        validMinutes = open?.validFor?.inWholeMinutes ?: 0,
                    )
                }
            }
        }
    }

    fun startPairing() = scope.action(::fail) { devices.startPairing() }

    fun stopPairing() = scope.action(::fail) { devices.stopPairing() }

    fun revoke(id: String) = scope.action(::fail) { devices.revoke(id) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    // A code left open after the dialog closes could still be used, so closing ends it.
    fun close() {
        parent.launch { devices.stopPairing() }
        scope.cancel()
    }

    private fun fail(e: Exception) = mutable.update { it.copy(error = e.userMessage) }

    private companion object {
        const val CODE_GROUP = 4
    }
}
