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

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.CentralManager.ScanOptions
import no.nordicsemi.kotlin.ble.client.android.exception.ScanningFailedToStartException
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.core.AdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Bluetooth5AdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.PrimaryPhy
import no.nordicsemi.kotlin.ble.environment.android.mock.LatestApi
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val LEGACY_DEVICE = "CA:FE:00:00:00:01"
private const val EXTENDED_DEVICE = "CA:FE:00:00:00:02"
private const val CODED_DEVICE = "CA:FE:00:00:00:03"
private const val NAME = "Mock Device"

/**
 * Tests of the Android-specific scan options in the mock central manager.
 */
class ScanOptionsTest {

    private fun device(address: String, parameters: AdvertisingSetParameters) =
        PeripheralSpec.simulatePeripheral(address) {
            if (parameters.connectable) {
                connectable(
                    name = NAME,
                    eventHandler = object : PeripheralSpecEventHandler {},
                    services = {},
                )
            }
            advertising(parameters = parameters) {
                CompleteLocalName(NAME)
            }
        }

    /**
     * Devices advertising every second: one with legacy advertising, one with extended advertising
     * on LE 1M and one with extended advertising on LE Coded.
     */
    private fun advertisingDevices() = listOf(
        device(LEGACY_DEVICE, LegacyAdvertisingSetParameters(connectable = false, interval = 1.seconds)),
        device(EXTENDED_DEVICE, Bluetooth5AdvertisingSetParameters(connectable = false, interval = 1.seconds)),
        device(
            CODED_DEVICE,
            Bluetooth5AdvertisingSetParameters(connectable = false, interval = 1.seconds, primaryPhy = PrimaryPhy.PHY_LE_CODED),
        ),
    )

    /** A connectable device advertising every second. */
    private fun connectableDevice() =
        device(LEGACY_DEVICE, LegacyAdvertisingSetParameters(connectable = true, interval = 1.seconds))

    /**
     * Starts a scan with the given options, or with the default ones if `null`, lets the devices
     * advertise 4 times, and returns the addresses of the reported results.
     */
    private fun TestScope.scannedAddresses(
        devices: List<PeripheralSpec<String>>,
        options: ScanOptions? = null,
        environment: MockAndroidEnvironment = LatestApi(),
    ): List<String> {
        val manager = CentralManager.mock(environment, backgroundScope)
        val received = mutableListOf<String>()
        backgroundScope.launch {
            val results = if (options != null) manager.scan(options = options) else manager.scan()
            results.collect { received += it.peripheral.identifier }
        }
        runCurrent()

        manager.simulatePeripherals(devices)
        advanceTimeBy(3500.milliseconds)
        runCurrent()
        return received
    }

    @Test
    fun `default scan reports all devices`() = runTest {
        val received = scannedAddresses(advertisingDevices())
        assertEquals(setOf(LEGACY_DEVICE, EXTENDED_DEVICE, CODED_DEVICE), received.toSet())
    }

    @Test
    fun `legacy-only scan does not report extended advertisements`() = runTest {
        val received = scannedAddresses(advertisingDevices(), ScanOptions(legacyOnly = true))
        assertEquals(setOf(LEGACY_DEVICE), received.toSet())
    }

    @Test
    fun `phones without extended advertising support report only legacy advertisements`() = runTest {
        // All phones before Android 8 lack the support, and so do some newer ones.
        val android6 = scannedAddresses(advertisingDevices(), environment = MockAndroidEnvironment.Api23())
        val android17 = scannedAddresses(
            devices = advertisingDevices(),
            environment = MockAndroidEnvironment.Api37(isLeExtendedAdvertisingSupported = false),
        )
        assertEquals(setOf(LEGACY_DEVICE), android6.toSet())
        assertEquals(setOf(LEGACY_DEVICE), android17.toSet())
    }

    @Test
    fun `passive scan keeps reporting a connectable device`() = runTest {
        // A phone that sends only one Scan Request to a connectable device per scan, so an active
        // scan reports such a device only once. A passive scan doesn't send Scan Requests at all.
        val environment = MockAndroidEnvironment.Api37(issueOnlyOneActiveScan = true)

        val active = scannedAddresses(listOf(connectableDevice()), environment = environment)
        val passive = scannedAddresses(listOf(connectableDevice()), ScanOptions(passive = true), environment)
        assertEquals(1, active.size)
        assertEquals(4, passive.size)
    }

    @Test
    fun `passive scan is ignored before Android 16`() = runTest {
        // The Nexus 4 runs Android 5.1 and sends only one Scan Request to a connectable device per scan.
        val received = scannedAddresses(listOf(connectableDevice()), ScanOptions(passive = true), MockAndroidEnvironment.Nexus4())
        assertEquals(1, received.size)
    }

    @Test
    fun `scan on LE 1M does not report advertisements on LE Coded`() = runTest {
        val received = scannedAddresses(advertisingDevices(), ScanOptions(phy = PrimaryPhy.PHY_LE_1M))
        assertEquals(setOf(LEGACY_DEVICE, EXTENDED_DEVICE), received.toSet())
    }

    @Test
    fun `scan on LE Coded reports only advertisements on LE Coded`() = runTest {
        val received = scannedAddresses(advertisingDevices(), ScanOptions(phy = PrimaryPhy.PHY_LE_CODED))
        assertEquals(setOf(CODED_DEVICE), received.toSet())
    }

    @Test
    fun `scan on LE Coded fails to start without LE Coded support`() = runTest {
        val environment = MockAndroidEnvironment.Api37(isLeCodedPhySupported = false)
        val manager = CentralManager.mock(environment, backgroundScope)

        val exception = assertFailsWith<ScanningFailedToStartException> {
            manager.scan(timeout = 1.seconds, options = ScanOptions(phy = PrimaryPhy.PHY_LE_CODED)).toList()
        }
        assertEquals(ScanningFailedToStartException.Reason.FeatureUnsupported, exception.reason)
    }

    @Test
    fun `legacy-only scan ignores the PHY`() = runTest {
        // Legacy advertisements are always sent on LE 1M, so the PHY doesn't apply. The scan starts
        // and reports them even on a phone without LE Coded support.
        val received = scannedAddresses(
            devices = advertisingDevices(),
            options = ScanOptions(legacyOnly = true, phy = PrimaryPhy.PHY_LE_CODED),
            environment = MockAndroidEnvironment.Api37(isLeCodedPhySupported = false),
        )
        assertEquals(setOf(LEGACY_DEVICE), received.toSet())
    }
}
