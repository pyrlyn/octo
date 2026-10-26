package octo.core.access

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.io.AtomicFile
import octo.core.io.ReadOnlyFileException
import octo.core.tempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Predictable bytes, and a stand-in hash that is plainly not the token; real SHA-256 is tested on the JVM. */
internal class FakeSecrets : Secrets {
    private var next = 0

    override fun randomBytes(size: Int) = ByteArray(size) { (next++).toByte() }

    override fun sha256(bytes: ByteArray) = bytes.reversedArray() + 0
}

class DevicesTest {
    private val file = AtomicFile(Path(tempDir("octo-devices"), "devices.json"))
    private val time = TestTimeSource()

    private suspend fun devices() = Devices.open(file, FakeSecrets(), time)

    @Test
    fun aCodePairsOneDeviceOnceAndOnlyAHashIsKept() =
        runTest {
            val devices = devices()
            val code = devices.startPairing()
            assertEquals(Devices.CODE_LENGTH, code.code.length)
            assertEquals(code, devices.pairing.value)

            val typed =
                code.code
                    .lowercase()
                    .chunked(4)
                    .joinToString("-")
            val token = devices.pair(typed, "  Phone ")
            assertNull(devices.pairing.value, "a code works once")
            assertEquals("Phone", assertNotNull(devices.device(token)).name)
            assertEquals("Phone", assertNotNull(devices.device(token)).name)
            assertNull(devices.device("guess"))
            assertNull(devices.device(null))
            assertFailsWith<PairingException> { devices.pair(code.code, "Again") }

            val saved = assertNotNull(file.read())
            assertFalse(token in saved, "the file must not hold the token")
            assertNotNull(Devices.open(file, FakeSecrets(), time).device(token), "pairing survives a restart")
        }

    @Test
    fun codesExpireAndDieAfterTooManyWrongTries() =
        runTest {
            val devices = devices()
            devices.startPairing()
            time += Devices.CODE_LIFETIME + 1.seconds
            assertFailsWith<PairingException> { devices.pair(devices.pairing.value!!.code, "Late") }
            assertNull(devices.pairing.value)

            val code = devices.startPairing().code
            repeat(Devices.MAX_TRIES) { assertFailsWith<PairingException> { devices.pair("WRONG", "Phone") } }
            assertNull(devices.pairing.value)
            assertFailsWith<PairingException> { devices.pair(code, "Phone") }
            assertEquals(emptyList(), devices.devices.value)

            val fresh = devices.startPairing()
            time += 4.minutes
            assertNotNull(devices.device(devices.pair(fresh.code, "Phone")))
        }

    @Test
    fun badNamesAreRefusedWithoutSpendingTheCode() =
        runTest {
            val devices = devices()
            val code = devices.startPairing().code
            assertFailsWith<IllegalArgumentException> { devices.pair(code, " ") }
            assertFailsWith<IllegalArgumentException> { devices.pair(code, "a\nb") }
            assertFailsWith<IllegalArgumentException> { devices.pair(code, "x".repeat(Devices.MAX_NAME + 1)) }
            assertNotNull(devices.pairing.value)
            devices.pair(code, "Phone")
        }

    @Test
    fun revokingLocksADeviceOut() =
        runTest {
            val devices = devices()
            val phone = devices.pair(devices.startPairing().code, "Phone")
            val laptop = devices.pair(devices.startPairing().code, "Laptop")
            assertNotEquals(phone, laptop)
            devices.revoke(assertNotNull(devices.device(phone)).id)
            assertNull(devices.device(phone))
            assertNotNull(devices.device(laptop))
        }

    @Test
    fun aBrokenOrNewerFileIsLeftAloneAndPairingIsRefused() =
        runTest {
            for (text in listOf("{broken", """{"version": 2, "devices": []}""")) {
                file.write(text)
                val devices = devices()
                assertTrue(devices.warning!!.contains("devices.json"), devices.warning)
                assertFailsWith<ReadOnlyFileException> { devices.startPairing() }
                assertEquals(text, file.read())
            }
        }
}
