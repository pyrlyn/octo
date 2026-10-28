package octo.cli

import com.github.ajalt.clikt.core.CliktError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import octo.core.access.ApiJson
import octo.core.access.RemoteApi
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Octo's remote API at [url]; every failure comes back as the error the user reads. */
class OctoClient(
    private val url: String,
    private val token: String?,
    private val http: HttpClient,
) {
    /** The body as Octo sent it, for `--json`. */
    fun get(path: String): String = send(request(path).GET())

    fun <T> get(
        path: String,
        serializer: KSerializer<T>,
    ): T = decode(get(path), serializer)

    fun <T> post(
        path: String,
        serializer: KSerializer<T>,
        body: T,
    ): String =
        send(
            request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ApiJson.encodeToString(serializer, body))),
        )

    fun <T> decode(
        body: String,
        serializer: KSerializer<T>,
    ): T =
        try {
            ApiJson.decodeFromString(serializer, body)
        } catch (e: SerializationException) {
            throw CliktError("Octo at $url sent an answer this octo command cannot read: ${e.message}", e)
        }

    private fun request(path: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(url.trimEnd('/') + RemoteApi.PREFIX + path))
        token?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private fun send(request: HttpRequest.Builder): String {
        val response =
            try {
                http.send(request.build(), HttpResponse.BodyHandlers.ofString())
            } catch (e: IOException) {
                throw CliktError(
                    "Could not reach Octo at $url (${e.message ?: e::class.simpleName}). Is it running with remote-access on?",
                    e,
                )
            }
        if (response.statusCode() in SUCCESS) return response.body()
        throw CliktError(message(response.body()) ?: "Octo at $url answered ${response.statusCode()}")
    }

    // An unreadable error body still fails the call, just with the status code instead.
    @Suppress("SwallowedException")
    private fun message(body: String): String? =
        try {
            ApiJson
                .parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.content
        } catch (e: IllegalArgumentException) {
            null
        }

    private companion object {
        val SUCCESS = 200..299
    }
}
