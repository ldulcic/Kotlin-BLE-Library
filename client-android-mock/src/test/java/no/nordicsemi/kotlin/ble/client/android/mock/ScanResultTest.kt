/*
 * Copyright (c) 2026, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.kotlin.ble.client.android.mock

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.RemoteServices
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ScanResult
import no.nordicsemi.kotlin.ble.client.mock.AddressType
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.core.AdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Bluetooth5AdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Phy
import no.nordicsemi.kotlin.ble.core.PrimaryPhy
import no.nordicsemi.kotlin.ble.core.TxPowerLevel
import no.nordicsemi.kotlin.ble.core.exception.BluetoothUnavailableException
import no.nordicsemi.kotlin.ble.core.exception.ManagerClosedException
import no.nordicsemi.kotlin.ble.core.util.fromShortUuid
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val ADDRESS = "CA:FE:00:00:00:01"
private const val OTHER_ADDRESS = "CA:FE:00:00:00:02"
private const val NEW_ADDRESS = "FA:FE:00:00:00:09"
private const val NAME = "Sensor"
private const val GAP_NAME = "Sensor GAP"
private val HEART_RATE = Uuid.fromShortUuid(0x180D)

/**
 * Tests of the content of the results reported by `CentralManager.scan()` and of
 * which advertisements make it to the results.
 */
class ScanResultTest {

    private fun legacy(connectable: Boolean, interval: Duration = 100.milliseconds) =
        LegacyAdvertisingSetParameters(connectable = connectable, interval = interval)

    /** A device advertising its name using the given parameters. */
    private fun device(
        address: String = ADDRESS,
        parameters: AdvertisingSetParameters = legacy(connectable = false),
        proximity: Proximity = Proximity.IMMEDIATE,
        isBeacon: Boolean = false,
        name: String = NAME,
    ) = PeripheralSpec.simulatePeripheral(address, proximity = proximity) {
        advertising(parameters = parameters, isBeacon = isBeacon) {
            CompleteLocalName(name)
        }
    }

    /** A connectable device that advertises another name than the one in its GAP service. */
    private fun connectableDevice(
        isAdvertisingWhenConnected: Boolean = false,
        advertisedName: String = NAME,
    ) = PeripheralSpec.simulatePeripheral(ADDRESS) {
        connectable(
            name = GAP_NAME,
            eventHandler = object : PeripheralSpecEventHandler {},
            // A real device has at least the Generic Attribute service, and a discovery
            // returning no services is reported as failed.
            services = { GenericAttributeService() },
        )
        advertising(
            parameters = legacy(connectable = true),
            isAdvertisingWhenConnected = isAdvertisingWhenConnected,
        ) {
            CompleteLocalName(advertisedName)
        }
    }

    private fun TestScope.collect(
        manager: CentralManager,
    ): MutableList<ScanResult> {
        val results = mutableListOf<ScanResult>()
        backgroundScope.launch { manager.scan().collect { results += it } }
        runCurrent()
        return results
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    /** Scans for a second and returns all results. */
    private fun TestScope.scanFor(
        vararg devices: PeripheralSpec<String>,
        environment: MockAndroidEnvironment = MockAndroidEnvironment.Api37(),
    ): List<ScanResult> {
        val manager = CentralManager.mock(environment = environment, scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(devices.toList())
        advance(1.seconds)
        return results
    }

    // ---- Result fields ----

    @Test
    fun `immediate device is reported with strong rssi`() = runTest {
        val results = scanFor(device(proximity = Proximity.IMMEDIATE))

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.rssi in -50..-36 }, "RSSI: ${results.map { it.rssi }}")
    }

    @Test
    fun `near device is reported with medium rssi`() = runTest {
        val results = scanFor(device(proximity = Proximity.NEAR))

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.rssi in -70..-50 }, "RSSI: ${results.map { it.rssi }}")
    }

    @Test
    fun `far device is reported with weak rssi`() = runTest {
        val results = scanFor(device(proximity = Proximity.FAR))

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.rssi in -100..-80 }, "RSSI: ${results.map { it.rssi }}")
    }

    @Test
    fun `result contains the advertised data`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {
            advertising(parameters = legacy(connectable = false)) {
                CompleteLocalName(NAME)
                ServiceUuid(HEART_RATE)
                ManufacturerData(0x0059, byteArrayOf(0x01, 0x02))
            }
        }

        val result = scanFor(spec).first()

        assertEquals(NAME, result.advertisingData.name)
        assertEquals(listOf(HEART_RATE), result.advertisingData.serviceUuids)
        assertContentEquals(byteArrayOf(0x01, 0x02), result.advertisingData.manufacturerData[0x0059])
    }

    @Test
    fun `connectable device is reported as connectable`() = runTest {
        val result = scanFor(device(parameters = legacy(connectable = true))).first()

        assertEquals(true, result.isConnectable)
    }

    @Test
    fun `non-connectable device is reported as not connectable`() = runTest {
        val result = scanFor(device(parameters = legacy(connectable = false))).first()

        assertEquals(false, result.isConnectable)
    }

    @Test
    fun `connectable flag is not available before Android 8`() = runTest {
        val results = scanFor(
            device(parameters = legacy(connectable = true)),
            environment = MockAndroidEnvironment.Api23(),
        )

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.isConnectable == null })
    }

    @Test
    fun `legacy advertisement is reported on 1M PHY with no secondary PHY and no tx power`() = runTest {
        val result = scanFor(device(parameters = legacy(connectable = false))).first()

        assertEquals(PrimaryPhy.PHY_LE_1M, result.primaryPhy)
        assertNull(result.secondaryPhy)
        assertNull(result.txPowerLevel)
    }

    @Test
    fun `extended advertisement reports its PHYs and tx power`() = runTest {
        val parameters = Bluetooth5AdvertisingSetParameters(
            connectable = false,
            interval = 100.milliseconds,
            txPowerLevel = TxPowerLevel.HIGH,
            includeTxPowerLevel = true,
            primaryPhy = PrimaryPhy.PHY_LE_1M,
            secondaryPhy = Phy.PHY_LE_2M,
        )

        val result = scanFor(device(parameters = parameters)).first()

        assertEquals(PrimaryPhy.PHY_LE_1M, result.primaryPhy)
        assertEquals(Phy.PHY_LE_2M, result.secondaryPhy)
        assertEquals(TxPowerLevel.HIGH, result.txPowerLevel)
    }

    @Test
    fun `extended advertisement without tx power in the packet reports no tx power`() = runTest {
        val parameters = Bluetooth5AdvertisingSetParameters(
            connectable = false,
            interval = 100.milliseconds,
            txPowerLevel = TxPowerLevel.HIGH,
            includeTxPowerLevel = false,
        )

        val result = scanFor(device(parameters = parameters)).first()

        assertNull(result.txPowerLevel)
    }

    @Test
    fun `advertisement on LE Coded PHY is reported when scanning on Coded PHY is supported`() = runTest {
        val parameters = Bluetooth5AdvertisingSetParameters(
            connectable = false,
            interval = 100.milliseconds,
            primaryPhy = PrimaryPhy.PHY_LE_CODED,
            secondaryPhy = Phy.PHY_LE_CODED,
        )

        val result = scanFor(device(parameters = parameters)).first()

        assertEquals(PrimaryPhy.PHY_LE_CODED, result.primaryPhy)
        assertEquals(Phy.PHY_LE_CODED, result.secondaryPhy)
    }

    @Test
    fun `advertisement on LE Coded PHY is not reported when the phone does not support it`() = runTest {
        val parameters = Bluetooth5AdvertisingSetParameters(
            connectable = false,
            interval = 100.milliseconds,
            primaryPhy = PrimaryPhy.PHY_LE_CODED,
            secondaryPhy = Phy.PHY_LE_CODED,
        )

        val results = scanFor(
            device(parameters = parameters),
            environment = MockAndroidEnvironment.Api37(isLeCodedPhySupported = false),
        )

        assertEquals(emptyList(), results)
    }

    @Test
    fun `advertisement with LE 2M secondary PHY is not reported when the phone does not support it`() = runTest {
        val parameters = Bluetooth5AdvertisingSetParameters(
            connectable = false,
            interval = 100.milliseconds,
            secondaryPhy = Phy.PHY_LE_2M,
        )

        val results = scanFor(
            device(parameters = parameters),
            environment = MockAndroidEnvironment.Api37(isLe2MPhySupported = false),
        )

        assertEquals(emptyList(), results)
    }

    // ---- Peripheral identity ----

    @Test
    fun `result peripheral has the address of the device`() = runTest {
        val result = scanFor(device(ADDRESS)).first()

        assertEquals(ADDRESS, result.peripheral.identifier)
        assertEquals(ADDRESS, result.peripheral.address)
    }

    @Test
    fun `all results of one device share the same peripheral instance`() = runTest {
        val results = scanFor(device(ADDRESS))

        assertTrue(results.size > 1)
        assertTrue(results.all { it.peripheral === results.first().peripheral })
    }

    @Test
    fun `different devices are reported with different peripheral instances`() = runTest {
        val results = scanFor(device(ADDRESS), device(OTHER_ADDRESS))

        val first = results.first { it.peripheral.identifier == ADDRESS }.peripheral
        val second = results.first { it.peripheral.identifier == OTHER_ADDRESS }.peripheral
        assertNotSame(first, second)
    }

    @Test
    fun `concurrent scans report the same peripheral instance`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        val first = collect(manager)
        val second = collect(manager)
        manager.simulatePeripherals(listOf(device()))
        advance(1.seconds)

        assertSame(first.first().peripheral, second.first().peripheral)
    }

    @Test
    fun `peripheral retrieved by id after scanning is the scanned one`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(device()))
        advance(1.seconds)

        assertSame(results.first().peripheral, manager.getPeripheralById(ADDRESS))
    }

    @Test
    fun `peripheral scanned after it was retrieved by id is the retrieved one`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        manager.simulatePeripherals(listOf(device()))
        val retrieved = checkNotNull(manager.getPeripheralById(ADDRESS))

        val results = collect(manager)
        advance(1.seconds)

        assertSame(retrieved, results.first().peripheral)
    }

    // ---- Retrieving peripherals by id ----

    @Test
    fun `peripheral of an address that was never scanned is available`() = runTest {
        // Like BluetoothAdapter.getRemoteDevice(), any valid address gives a peripheral.
        val manager = CentralManager.mock(scope = backgroundScope)

        val peripheral = manager.getPeripheralById(OTHER_ADDRESS)

        assertNotNull(peripheral)
        assertEquals(OTHER_ADDRESS, peripheral.identifier)
    }

    @Test
    fun `retrieving peripherals by id returns them in the requested order`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        manager.simulatePeripherals(listOf(device(ADDRESS), device(OTHER_ADDRESS)))

        val peripherals = manager.getPeripheralsById(listOf(OTHER_ADDRESS, ADDRESS))

        assertEquals(listOf(OTHER_ADDRESS, ADDRESS), peripherals.map { it.identifier })
    }

    @Test
    fun `retrieving the same id twice returns the same peripheral`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)

        assertSame(manager.getPeripheralById(ADDRESS), manager.getPeripheralById(ADDRESS))
    }

    @Test
    fun `retrieving a peripheral by an invalid address throws IllegalArgumentException`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)

        assertFailsWith<IllegalArgumentException> { manager.getPeripheralById("not an address") }
    }

    @Test
    fun `retrieving peripherals from a closed manager throws ManagerClosedException`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        manager.close()

        assertFailsWith<ManagerClosedException> { manager.getPeripheralById(ADDRESS) }
    }

    @Test
    @Ignore("getPeripheralById in the mock does not check the Bluetooth state.")
    fun `retrieving peripherals with Bluetooth turned off throws BluetoothUnavailableException`() = runTest {
        val environment = MockAndroidEnvironment.Api37(isBluetoothEnabled = false)
        val manager = CentralManager.mock(environment = environment, scope = backgroundScope)

        assertFailsWith<BluetoothUnavailableException> { manager.getPeripheralById(ADDRESS) }
    }

    // ---- Range ----

    @Test
    fun `device that is out of range is not reported`() = runTest {
        val results = scanFor(device(proximity = Proximity.OUT_OF_RANGE))

        assertEquals(emptyList(), results)
    }

    @Test
    fun `device moved out of range stops being reported`() = runTest {
        val spec = device()
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(spec))
        advance(1.seconds)
        assertTrue(results.isNotEmpty())

        spec.simulateProximityChange(Proximity.OUT_OF_RANGE)
        advance(100.milliseconds)
        val countWhenLeft = results.size
        advance(5.seconds)

        assertEquals(countWhenLeft, results.size)
    }

    @Test
    fun `device that came back in range is reported again`() = runTest {
        val spec = device(proximity = Proximity.OUT_OF_RANGE)
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(spec))
        advance(2.seconds)
        assertEquals(emptyList(), results)

        spec.simulateProximityChange(Proximity.NEAR)
        advance(1.seconds)

        assertTrue(results.isNotEmpty())
        assertTrue(results.none { it.rssi == -128 })
    }

    @Test
    fun `device moved to another proximity is reported with the new rssi`() = runTest {
        val spec = device(proximity = Proximity.IMMEDIATE)
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(spec))
        advance(1.seconds)
        results.clear()

        spec.simulateProximityChange(Proximity.FAR)
        advance(1.seconds)

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.rssi in -100..-80 }, "RSSI: ${results.map { it.rssi }}")
    }

    // ---- Beacons ----

    @Test
    fun `beacon is not reported when location is not used for scanning`() = runTest {
        // From Android 12 apps declaring neverForLocation do not get beacons.
        val results = scanFor(
            device(isBeacon = true),
            environment = MockAndroidEnvironment.Api37(isNeverForLocationFlagSet = true),
        )

        assertEquals(emptyList(), results)
    }

    @Test
    fun `beacon is reported when the app may use scan results to derive location`() = runTest {
        val results = scanFor(
            device(isBeacon = true),
            environment = MockAndroidEnvironment.Api37(isNeverForLocationFlagSet = false),
        )

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `non-beacon device is reported when the app never uses scan results for location`() = runTest {
        val results = scanFor(
            device(isBeacon = false),
            environment = MockAndroidEnvironment.Api37(isNeverForLocationFlagSet = true),
        )

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `beacon is reported before Android 12`() = runTest {
        val results = scanFor(
            device(isBeacon = true),
            environment = MockAndroidEnvironment.Api23(),
        )

        assertTrue(results.isNotEmpty())
    }

    // ---- Advertising limits ----

    @Test
    fun `device advertising a limited number of events is reported exactly that many times`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {
            advertising(
                parameters = legacy(connectable = false),
                maxAdvertisingEvents = 3,
            ) {
                CompleteLocalName(NAME)
            }
        }

        val results = scanFor(spec)
        advance(10.seconds)

        assertEquals(3, results.size)
    }

    @Test
    fun `device advertising with a timeout stops being reported after it`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {
            advertising(
                parameters = legacy(connectable = false),
                timeout = 1.seconds,
            ) {
                CompleteLocalName(NAME)
            }
        }
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(spec))

        advance(1.5.seconds)
        val countAfterTimeout = results.size
        advance(10.seconds)

        assertTrue(countAfterTimeout > 0)
        assertEquals(countAfterTimeout, results.size)
    }

    @Test
    fun `device advertising with a delay is not reported before the delay`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {
            advertising(
                parameters = legacy(connectable = false),
                delay = 2.seconds,
            ) {
                CompleteLocalName(NAME)
            }
        }
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(spec))

        advance(1.seconds)
        assertEquals(emptyList(), results)
        advance(2.seconds)

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `device with several advertising sets is reported with the data of each set`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {
            advertising(parameters = legacy(connectable = false)) { CompleteLocalName("First") }
            advertising(parameters = legacy(connectable = false)) { CompleteLocalName("Second") }
        }

        val results = scanFor(spec)

        assertEquals(
            setOf("First", "Second"),
            results.mapNotNull { it.advertisingData.name }.toSet(),
        )
        assertEquals(setOf(ADDRESS), results.map { it.peripheral.identifier }.toSet())
    }

    @Test
    fun `device without advertising sets is never reported`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {}

        val results = scanFor(spec)

        assertEquals(emptyList(), results)
    }

    // ---- Connected devices ----

    @Test
    fun `connected device that does not advertise when connected is not reported`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(connectableDevice(isAdvertisingWhenConnected = false)))
        advance(1.seconds)
        manager.connect(results.first().peripheral)
        runCurrent()
        val countWhenConnected = results.size

        advance(5.seconds)

        assertEquals(countWhenConnected, results.size)
    }

    @Test
    fun `connected device that advertises when connected is still reported`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(connectableDevice(isAdvertisingWhenConnected = true)))
        advance(1.seconds)
        manager.connect(results.first().peripheral)
        runCurrent()
        val countWhenConnected = results.size

        advance(5.seconds)

        assertTrue(results.size > countWhenConnected)
    }

    @Test
    fun `device is reported again after it disconnected`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(connectableDevice(isAdvertisingWhenConnected = false)))
        advance(1.seconds)
        val peripheral = results.first().peripheral
        manager.connect(peripheral)
        advance(2.seconds)
        peripheral.disconnect()
        runCurrent()
        val countWhenDisconnected = results.size

        advance(2.seconds)

        assertTrue(results.size > countWhenDisconnected)
    }

    // ---- Phone quirks ----

    @Test
    fun `phone sending a single scan request reports a connectable device only once per scan`() = runTest {
        val results = scanFor(
            device(parameters = legacy(connectable = true)),
            environment = MockAndroidEnvironment.Api23(issueOnlyOneActiveScan = true),
        )

        assertEquals(1, results.size)
    }

    @Test
    fun `phone sending a single scan request reports a non-connectable device continuously`() = runTest {
        val results = scanFor(
            device(parameters = legacy(connectable = false)),
            environment = MockAndroidEnvironment.Api23(issueOnlyOneActiveScan = true),
        )

        assertTrue(results.size > 1)
    }

    @Test
    fun `phone sending a single scan request reports a connectable device again in the next scan`() = runTest {
        val environment = MockAndroidEnvironment.Api23(issueOnlyOneActiveScan = true)
        val manager = CentralManager.mock(environment = environment, scope = backgroundScope)
        manager.simulatePeripherals(listOf(device(parameters = legacy(connectable = true))))
        val first = collect(manager)
        advance(1.seconds)
        first.clear()

        val second = collect(manager)
        advance(1.seconds)

        assertEquals(1, second.size)
    }

    // ---- Address change ----

    @Test
    fun `device that changed its address is reported with the new address`() = runTest {
        val spec = device(ADDRESS)
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(spec))
        advance(1.seconds)
        val oldPeripheral = results.first().peripheral
        results.clear()

        spec.simulateMacChange(NEW_ADDRESS, AddressType.RANDOM_STATIC)
        advance(1.seconds)

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.peripheral.identifier == NEW_ADDRESS })
        assertNotSame(oldPeripheral, results.first().peripheral)
    }

    @Test
    fun `address filter follows the new address of a device that changed it`() = runTest {
        val spec = device(ADDRESS)
        val manager = CentralManager.mock(scope = backgroundScope)
        val oldAddressResults = mutableListOf<ScanResult>()
        val newAddressResults = mutableListOf<ScanResult>()
        backgroundScope.launch { manager.scan { Address(ADDRESS) }.collect { oldAddressResults += it } }
        backgroundScope.launch { manager.scan { Address(NEW_ADDRESS) }.collect { newAddressResults += it } }
        runCurrent()
        manager.simulatePeripherals(listOf(spec))
        advance(1.seconds)
        oldAddressResults.clear()

        spec.simulateMacChange(NEW_ADDRESS, AddressType.RANDOM_STATIC)
        advance(1.seconds)

        assertEquals(emptyList(), oldAddressResults)
        assertTrue(newAddressResults.isNotEmpty())
    }

    // ---- Name ----

    @Test
    fun `scanned peripheral has the name from the advertisement`() = runTest {
        val result = scanFor(connectableDevice()).first()

        assertEquals(NAME, result.peripheral.name)
    }

    @Test
    fun `peripheral without a name in the advertisement has no name`() = runTest {
        val spec = PeripheralSpec.simulatePeripheral(ADDRESS) {
            advertising(parameters = legacy(connectable = false)) { ServiceUuid(HEART_RATE) }
        }

        val result = scanFor(spec).first()

        assertNull(result.peripheral.name)
    }

    @Test
    fun `peripheral name is the device name from GAP after services were discovered`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        val results = collect(manager)
        manager.simulatePeripherals(listOf(connectableDevice()))
        advance(1.seconds)
        val peripheral = results.first().peripheral
        assertEquals(NAME, peripheral.name)

        manager.connect(peripheral)
        val services = peripheral.services()
        advance(5.seconds)

        assertIs<RemoteServices.Discovered>(services.value)
        assertEquals(GAP_NAME, peripheral.name)
    }
}
