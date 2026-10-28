package octo.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import octo.core.config.RemoteAccessConfig
import octo.core.paths.HostOs
import octo.core.paths.OctoPaths
import java.net.http.HttpClient
import java.nio.file.Path

/**
 * The `octo` command: drives a running Octo through its remote API, as a paired device would.
 * Everything it reads from outside comes in through the constructor, so tests never see the
 * real environment or `HOME`.
 */
class Octo(
    private val env: Map<String, String>,
    private val userHome: String,
    internal val cwd: String,
    private val os: HostOs,
    private val http: HttpClient = HttpClient.newHttpClient(),
    internal val sleep: (Long) -> Unit = Thread::sleep,
    internal val now: () -> Long = System::currentTimeMillis,
) : CliktCommand(name = "octo") {
    private val url by option("--url", help = "Octo's remote API; defaults to OCTO_URL, then the paired one")

    init {
        subcommands(
            PairCommand(this),
            TaskCommand().subcommands(
                TaskListCommand(this),
                TaskStartCommand(this),
                TaskSendCommand(this),
                TaskReadCommand(this),
                TaskWaitCommand(this),
            ),
        )
    }

    override fun help(context: Context) =
        "Drive Octo from scripts. Turn on the remote-access flag in Octo, then pair with a code from its Devices dialog."

    override fun run() = Unit

    internal val credentials by lazy { CredentialsFile(Path.of(OctoPaths.resolve(env, os, userHome).cli.toString())) }

    private val stored by lazy { credentials.read() }

    internal val address: String
        get() = url ?: env.value("OCTO_URL") ?: stored?.url ?: DEFAULT_URL

    /** A client for [address]; a stored token goes only to the Octo it was paired with. */
    internal fun client(paired: Boolean = true): OctoClient {
        val address = address
        val token = env.value("OCTO_TOKEN") ?: stored?.takeIf { it.url == address }?.token
        if (paired && token == null) {
            throw CliktError(
                "Not paired with Octo at $address. Run octo pair <code> with a code from Octo's Devices dialog.",
            )
        }
        return OctoClient(address, token, http)
    }

    private fun Map<String, String>.value(name: String) = get(name)?.takeIf { it.isNotBlank() }

    private companion object {
        val DEFAULT_URL = "http://127.0.0.1:${RemoteAccessConfig.DEFAULT_PORT}"
    }
}
