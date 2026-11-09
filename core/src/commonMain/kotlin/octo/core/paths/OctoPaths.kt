package octo.core.paths

import kotlinx.io.files.Path

enum class HostOs { MacOs, Linux, Windows }

/** Where Octo keeps its files. Resolved once at start-up; the core never reads the environment itself. */
data class OctoPaths(
    val home: Path,
    /**
     * A short directory private to this user for SSH control sockets, or null where there is none.
     * Not under [home]: ssh cannot use a socket path with spaces, and the path must stay short.
     */
    val sockets: Path? = null,
) {
    /** The SQLite database with everything Octo keeps except the configuration. */
    val database: Path get() = Path(home, "octo.db")
    val config: Path get() = Path(home, "config.json")
    val worktrees: Path get() = Path(home, "worktrees")
    val plugins: Path get() = Path(home, "plugins")

    /** The `octo` command's paired URL and token. */
    val cli: Path get() = Path(home, "cli.json")

    // The files Octo kept before the database; they are read once, to import them into it.
    val state: Path get() = Path(home, "state.json")
    val acknowledgements: Path get() = Path(home, "acknowledgements.json")
    val presets: Path get() = Path(home, "presets.json")
    val devices: Path get() = Path(home, "devices.json")
    val shares: Path get() = Path(home, "shares.json")
    val usage: Path get() = Path(home, "usage.json")
    val triggers: Path get() = Path(home, "triggers.json")

    companion object {
        fun resolve(
            env: Map<String, String>,
            os: HostOs,
            userHome: String,
        ): OctoPaths {
            val sockets = socketsDir(env, os)
            env["OCTO_HOME"]?.takeIf { it.isNotBlank() }?.let { return OctoPaths(Path(it), sockets) }
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
            return OctoPaths(home, sockets)
        }

        // macOS gives each user a private TMPDIR, and Linux sessions a private XDG_RUNTIME_DIR.
        // Windows OpenSSH cannot share connections, so it gets none.
        private fun socketsDir(
            env: Map<String, String>,
            os: HostOs,
        ): Path? {
            val base =
                when (os) {
                    HostOs.MacOs -> env["TMPDIR"]
                    HostOs.Linux -> env["XDG_RUNTIME_DIR"]
                    HostOs.Windows -> null
                }
            return base?.takeIf { it.startsWith("/") }?.let { Path(it, "octo") }
        }
    }
}
