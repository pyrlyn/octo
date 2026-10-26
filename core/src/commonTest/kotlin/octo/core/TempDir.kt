package octo.core

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

/** A fresh directory under the system temp dir; tests never write to the real Octo home. */
fun tempDir(prefix: String): Path =
    Path(
        SystemTemporaryDirectory,
        "$prefix-${Random.nextLong().toULong()}",
    ).also { SystemFileSystem.createDirectories(it) }
