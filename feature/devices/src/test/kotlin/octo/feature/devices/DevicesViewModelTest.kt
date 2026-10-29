package octo.feature.devices

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.access.Devices
import octo.core.access.JvmSecrets
import octo.core.io.AtomicFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    private val file = Path(Files.createTempDirectory("octo-ui-devices").toString(), "devices.json")

    @Test
    fun pairsShowsAndRevokesDevices() =
        runTest {
            val devices = Devices.open(AtomicFile(file), JvmSecrets)
            val vm = DevicesViewModel(devices, "http://127.0.0.1:47470", backgroundScope)
            runCurrent()
            assertNull(vm.state.value.code)

            vm.startPairing()
            runCurrent()
            val shown = assertNotNull(vm.state.value.code)
            assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(shown), shown)
            assertEquals(5, vm.state.value.validMinutes)

            devices.pair(shown, "Phone")
            runCurrent()
            assertNull(vm.state.value.code, "a used code disappears")
            val phone =
                vm.state.value.devices
                    .single()
            assertEquals("Phone", phone.name)

            // The file is written off the test dispatcher, so wait for the list instead of the scheduler.
            vm.revoke(phone.id)
            vm.state.first { it.devices.isEmpty() }
        }

    @Test
    fun closingEndsAnOpenCode() =
        runTest {
            val devices = Devices.open(AtomicFile(file), JvmSecrets)
            val vm = DevicesViewModel(devices, "http://127.0.0.1:47470", backgroundScope)
            vm.startPairing()
            runCurrent()
            assertNotNull(devices.pairing.value)
            vm.close()
            runCurrent()
            assertNull(devices.pairing.value)
        }

    @Test
    fun aBrokenFileShowsWhyPairingFails() =
        runTest {
            AtomicFile(file).write("{broken")
            val vm = DevicesViewModel(Devices.open(AtomicFile(file), JvmSecrets), "http://127.0.0.1:1", backgroundScope)
            vm.startPairing()
            runCurrent()
            assertNull(vm.state.value.code)
            assertNotNull(vm.state.value.error)
        }
}
