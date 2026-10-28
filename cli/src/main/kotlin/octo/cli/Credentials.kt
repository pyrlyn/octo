package octo.cli

import com.github.ajalt.clikt.core.CliktError
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import octo.core.access.ApiJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/** What `octo pair` got: the token works only against the Octo at [url]. */
@Serializable
data class Credentials(
    val url: String,
    val token: String,
)

/** The paired [Credentials]; the token acts as the user, so only they may read the file. */
class CredentialsFile(
    val path: Path,
) {
    fun read(): Credentials? =
        if (!Files.exists(path)) {
            null
        } else {
            try {
                ApiJson.decodeFromString(Credentials.serializer(), Files.readString(path))
            } catch (e: SerializationException) {
                throw CliktError("$path cannot be read (${e.message}); run octo pair again", e)
            }
        }

    fun write(credentials: Credentials) {
        val dir = path.toAbsolutePath().parent
        Files.createDirectories(dir)
        // Created private, then moved into place, so the token is never readable by others.
        val temp =
            if (posix) {
                Files.createTempFile(dir, "cli", ".tmp", PosixFilePermissions.asFileAttribute(PRIVATE))
            } else {
                Files.createTempFile(dir, "cli", ".tmp")
            }
        Files.writeString(temp, ApiJson.encodeToString(Credentials.serializer(), credentials))
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private val posix get() = "posix" in path.fileSystem.supportedFileAttributeViews()

    private companion object {
        val PRIVATE = PosixFilePermissions.fromString("rw-------")
    }
}
