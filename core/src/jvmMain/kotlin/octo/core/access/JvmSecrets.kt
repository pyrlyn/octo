package octo.core.access

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object JvmSecrets : Secrets {
    private val random = SecureRandom()

    override fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    override fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    override fun hmacSha256(
        key: ByteArray,
        bytes: ByteArray,
    ): ByteArray = Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(bytes)

    private const val HMAC = "HmacSHA256"
}
