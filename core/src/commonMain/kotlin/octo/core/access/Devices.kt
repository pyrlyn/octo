package octo.core.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile
import octo.core.io.ReadOnlyFileException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The platform's cryptography: a secure random source, SHA-256 and HMAC-SHA256. */
interface Secrets {
    fun randomBytes(size: Int): ByteArray

    fun sha256(bytes: ByteArray): ByteArray

    fun hmacSha256(
        key: ByteArray,
        bytes: ByteArray,
    ): ByteArray
}

/** A device the user paired; only a hash of its token is kept, so the file alone lets nobody in. */
@Serializable
data class Device(
    val id: String,
    val name: String,
    val tokenHash: String,
)

/** A one-time code the user reads off the Octo window and types on the device. */
data class PairingCode(
    val code: String,
    val validFor: Duration,
)

class PairingException(
    message: String,
) : Exception(message)

/** Paired devices in `devices.json`, and the one pairing code that may be open at a time. */
class Devices private constructor(
    private val file: JsonListFile<Device>,
    private val secrets: Secrets,
    private val time: TimeSource,
) {
    private val lock = Mutex()
    private val open = MutableStateFlow<PairingCode?>(null)
    private var expires: TimeMark? = null
    private var triesLeft = 0

    val devices: StateFlow<List<Device>> = file.items

    /** The open code, cleared once it is used or has had too many wrong tries. */
    val pairing: StateFlow<PairingCode?> = open.asStateFlow()

    /** Set when `devices.json` could not be read; pairing and revoking are refused until it is fixed. */
    val warning: String? get() = file.warning

    /** Opens a fresh code, replacing any earlier one. */
    suspend fun startPairing(): PairingCode =
        lock.withLock {
            // Pairing could never finish, so do not hand out a code that cannot work.
            warning?.let { throw ReadOnlyFileException(it) }
            val bytes = secrets.randomBytes(CODE_LENGTH)
            // 32 letters, so each byte maps to one without bias; no 0/O or 1/I to misread.
            val code = CharArray(CODE_LENGTH) { ALPHABET[bytes[it].toInt() and (ALPHABET.length - 1)] }.concatToString()
            expires = time.markNow() + CODE_LIFETIME
            triesLeft = MAX_TRIES
            PairingCode(code, CODE_LIFETIME).also { open.value = it }
        }

    suspend fun stopPairing() = lock.withLock { close() }

    /** Pairs a device and returns its token; the token is shown this once and never stored. */
    suspend fun pair(
        code: String,
        name: String,
    ): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= MAX_NAME && clean.none { it.isISOControl() }) {
            "A device name is 1 to $MAX_NAME printable characters"
        }
        return lock.withLock {
            warning?.let { throw ReadOnlyFileException(it) }
            checkCode(code)
            close()
            val token = hex(secrets.randomBytes(TOKEN_BYTES))
            val device = Device(hex(secrets.randomBytes(ID_BYTES)), clean, hash(token))
            file.change { it + device }
            token
        }
    }

    /** The device holding [token], or null for a missing, unknown or revoked one. */
    fun device(token: String?): Device? {
        if (token == null) return null
        val hash = hash(token)
        return devices.value.firstOrNull { it.tokenHash == hash }
    }

    suspend fun revoke(id: String) = file.change { all -> all.filter { it.id != id } }

    private fun checkCode(code: String) {
        val current = open.value
        val alive = current != null && expires?.hasPassedNow() == false
        if (!alive) {
            close()
            throw PairingException("No pairing code is open; start one in Octo")
        }
        // People read codes off a screen, so case and the separator the UI shows do not matter.
        if (code.uppercase().filter { it != '-' && it != ' ' } != current.code) {
            triesLeft--
            if (triesLeft <= 0) close()
            throw PairingException("Wrong pairing code")
        }
    }

    private fun close() {
        open.value = null
        expires = null
        triesLeft = 0
    }

    private fun hash(token: String) = hex(secrets.sha256(token.encodeToByteArray()))

    companion object {
        const val CODE_LENGTH = 8
        const val MAX_TRIES = 5
        const val MAX_NAME = 60
        val CODE_LIFETIME = 5.minutes
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val TOKEN_BYTES = 32
        private const val ID_BYTES = 8

        suspend fun open(
            file: AtomicFile,
            secrets: Secrets,
            time: TimeSource = TimeSource.Monotonic,
        ) = Devices(JsonListFile.open(file, Device.serializer(), "devices"), secrets, time)

        private fun hex(bytes: ByteArray) = bytes.toHexString()
    }
}
