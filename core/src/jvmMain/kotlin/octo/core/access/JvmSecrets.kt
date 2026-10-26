package octo.core.access

import java.security.MessageDigest
import java.security.SecureRandom

object JvmSecrets : Secrets {
    private val random = SecureRandom()

    override fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    override fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
