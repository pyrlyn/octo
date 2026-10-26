package octo.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run

/** The directory checks a workspace needs, on whichever machine its worktrees live. */
interface Directories {
    suspend fun create(path: String)

    suspend fun exists(path: String): Boolean
}

class LocalDirectories(
    private val fs: FileSystem = SystemFileSystem,
) : Directories {
    override suspend fun create(path: String) = withContext(Dispatchers.IO) { fs.createDirectories(Path(path)) }

    override suspend fun exists(path: String) = withContext(Dispatchers.IO) { fs.exists(Path(path)) }
}

/** Directories on a POSIX machine reached through [launcher], such as one over SSH. */
class ShellDirectories(
    private val launcher: ProcessLauncher,
) : Directories {
    override suspend fun create(path: String) {
        val result = launcher.run(ProcessSpec("mkdir", listOf("-p", "--", path)))
        check(result.exitCode == 0) { "Could not create $path: ${result.stderr.trim()}" }
    }

    override suspend fun exists(path: String) = launcher.run(ProcessSpec("test", listOf("-d", path))).exitCode == 0
}
