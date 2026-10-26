package octo.core.paths

fun currentHostOs(): HostOs {
    val name = System.getProperty("os.name").lowercase()
    return when {
        name.contains("mac") -> HostOs.MacOs
        name.contains("windows") -> HostOs.Windows
        else -> HostOs.Linux
    }
}

fun OctoPaths.Companion.forCurrentHost(): OctoPaths =
    resolve(System.getenv(), currentHostOs(), System.getProperty("user.home"))
