package octo.testing

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Reads a file relative to the module directory; tests run from there (see `octo.kmp`). */
fun readFixture(path: String): String = SystemFileSystem.source(Path(path)).buffered().use { it.readString() }

/**
 * Plays a recorded session transcript. One entry per line:
 * - `> {json}` waits for Octo to write a line whose JSON contains every field given here;
 * - `< line` writes the line to stdout; `! line` writes it to stderr;
 * - `x <code>` exits; `#` lines and blank lines are ignored.
 */
suspend fun FakeProcess.replay(transcript: String) {
    for (raw in transcript.lineSequence()) {
        val line = raw.trimEnd()
        if (line.isBlank() || line.startsWith("#")) continue
        val body = line.drop(2)
        when (line.first()) {
            '>' -> {
                expectInput(body)
            }

            '<' -> {
                emit(body)
            }

            '!' -> {
                emitError(body)
            }

            'x' -> {
                exit(body.trim().toInt())
                return
            }

            else -> {
                error("Bad transcript line: $line")
            }
        }
    }
}

private suspend fun FakeProcess.expectInput(expected: String) {
    val actual = readLine() ?: throw AssertionError("Expected input $expected, but stdin was closed")
    if (expected.isBlank()) return
    val want = Json.parseToJsonElement(expected)
    val got = Json.parseToJsonElement(actual)
    if (!contains(got, want)) throw AssertionError("Expected input matching $expected, got $actual")
}

/** True when [actual] has everything [expected] has; extra fields in [actual] are allowed. */
fun contains(
    actual: JsonElement,
    expected: JsonElement,
): Boolean =
    when (expected) {
        is JsonObject -> {
            actual is JsonObject && expected.all { (k, v) -> actual[k]?.let { contains(it, v) } == true }
        }

        is JsonArray -> {
            actual is JsonArray && actual.size == expected.size &&
                expected.indices.all { contains(actual[it], expected[it]) }
        }

        else -> {
            actual == expected
        }
    }
