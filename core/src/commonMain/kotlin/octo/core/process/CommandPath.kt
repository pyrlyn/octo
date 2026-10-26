package octo.core.process

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A CLI found once on the launcher's PATH, so the PATH Octo was given wins over the JVM's own. */
class CommandPath(
    private val launcher: ProcessLauncher,
    private val command: String,
    private val notFound: () -> Exception,
) {
    private val lock = Mutex()
    private var resolved: String? = null

    suspend fun get(): String =
        lock.withLock {
            resolved ?: (launcher.which(command) ?: throw notFound()).also { resolved = it }
        }
}
