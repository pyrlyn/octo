package octo.feature.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** A scope a view model owns and cancels in its own `close()`, so screens can come and go. */
fun CoroutineScope.childScope(): CoroutineScope =
    CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))

/**
 * Runs a user action. A failing action must show as a message in the screen's state, never
 * crash the window, so every exception except cancellation goes to [onError].
 */
@Suppress("TooGenericExceptionCaught")
fun CoroutineScope.action(
    onError: (Exception) -> Unit,
    block: suspend () -> Unit,
): Job =
    launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

val Exception.userMessage: String get() = message ?: this::class.simpleName ?: "Something went wrong"
