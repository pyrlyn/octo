package octo.core.paths

import kotlinx.io.files.Path

enum class HostOs { MacOs, Linux, Windows }

/** Where Octo keeps its files. Resolved once at start-up; the core never reads the environment itself. */
data class OctoPaths(
    val home: Path,
) {
    val state: Path get() = Path(home, "state.json")
    val config: Path get() = Path(home, "config.json")
    val worktrees: Path get() = Path(home, "worktrees")
    val acknowledgements: Path get() = Path(home, "acknowledgements.json")
    val presets: Path get() = Path(home, "presets.json")

    companion object {
        fun resolve(
            env: Map<String, String>,
            os: HostOs,
            userHome: String,
        ): OctoPaths {
            env["OCTO_HOME"]?.takeIf { it.isNotBlank() }?.let { return OctoPaths(Path(it)) }
            val home =
                when (os) {
                    HostOs.MacOs -> {
                        Path(userHome, "Library", "Application Support", "Octo")
                    }

                    HostOs.Linux -> {
                        env["XDG_STATE_HOME"]?.takeIf { it.isNotBlank() }?.let { Path(it, "octo") }
                            ?: Path(userHome, ".local", "state", "octo")
                    }

                    HostOs.Windows -> {
                        env["APPDATA"]?.takeIf { it.isNotBlank() }?.let { Path(it, "Octo") }
                            ?: Path(userHome, "AppData", "Roaming", "Octo")
                    }
                }
            return OctoPaths(home)
        }
    }
}
