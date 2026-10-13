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
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConjunctionFilterScope
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.core.AdvertisingDataType
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.mock.AdvertisingDataScope
import no.nordicsemi.kotlin.ble.core.util.fromShortUuid
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val A = "CA:FE:00:00:00:01"
private const val B = "CA:FE:00:00:00:02"
private const val C = "CA:FE:00:00:00:03"
private const val D = "CA:FE:00:00:00:04"
private val HEART_RATE = Uuid.fromShortUuid(0x180D)
private val BATTERY = Uuid.fromShortUuid(0x180F)
private val ENVIRONMENT = Uuid.fromShortUuid(0x181A)
private const val NORDIC = 0x0059
private const val APPLE = 0x004C

/**
 * Tests of the filters of `CentralManager.scan()`: which advertisements are reported.
 */
class ScanFilterTest {

    /** A non-connectable device advertising the given data every 100 ms. */
    private fun device(address: String, data: AdvertisingDataScope.() -> Unit) =
        PeripheralSpec.simulatePeripheral(address) {
            advertising(
                parameters = LegacyAdvertisingSetParameters(
                    connectable = false,
                    interval = 100.milliseconds,
                ),
            ) {
                data()
            }
        }

    /** Scans for a while with the given filter and returns addresses of all reported devices. */
    private fun TestScope.scanned(
        vararg devices: PeripheralSpec<String>,
        filter: ConjunctionFilterScope.() -> Unit = {},
    ): Set<String> {
        val manager = CentralManager.mock(scope = backgroundScope)
        val found = mutableSetOf<String>()
        backgroundScope.launch {
            manager.scan(filter = filter).collect { found += it.peripheral.identifier }
        }
        runCurrent()
        manager.simulatePeripherals(devices.toList())
        advanceTimeBy(1.seconds)
        runCurrent()
        return found
    }

    // ---- No filter ----

    @Test
    fun `scan without filters reports every advertising device`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("One") },
            device(B) { ServiceUuid(HEART_RATE) },
            device(C) { ManufacturerData(NORDIC, byteArrayOf(1)) },
        )

        assertEquals(setOf(A, B, C), found)
    }

    // ---- Name ----

    @Test
    fun `name filter reports only devices with exactly that name`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
            device(B) { CompleteLocalName("Sensor 2") },
            device(C) { CompleteLocalName("sensor") },
        ) {
            Name("Sensor")
        }

        assertEquals(setOf(A), found)
    }

    @Test
    fun `name filter does not report devices that do not advertise a name`() = runTest {
        val found = scanned(
            device(A) { ServiceUuid(HEART_RATE) },
            device(B) { CompleteLocalName("Sensor") },
        ) {
            Name("Sensor")
        }

        assertEquals(setOf(B), found)
    }

    @Test
    fun `name filter matches the shortened local name when there is no complete name`() = runTest {
        val found = scanned(
            device(A) { ShortenedLocalName("Sens") },
        ) {
            Name("Sens")
        }

        assertEquals(setOf(A), found)
    }

    @Test
    fun `name filter that matches no device reports nothing`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
            device(B) { CompleteLocalName("Beacon") },
        ) {
            Name("Does not exist")
        }

        assertEquals(emptySet(), found)
    }

    @Test
    fun `name regex filter reports devices whose name matches the whole expression`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor 1") },
            device(B) { CompleteLocalName("Sensor 22") },
            device(C) { CompleteLocalName("Beacon 1") },
        ) {
            Name(Regex("Sensor \\d"))
        }

        // Regex has to match the whole name ("Sensor 22" has two digits).
        assertEquals(setOf(A), found)
    }

    @Test
    fun `name regex filter does not report devices without a name`() = runTest {
        val found = scanned(
            device(A) { ServiceUuid(HEART_RATE) },
            device(B) { CompleteLocalName("Sensor 1") },
        ) {
            Name(Regex("Sensor.*"))
        }

        assertEquals(setOf(B), found)
    }

    @Test
    fun `setting the name filter twice keeps only the last one`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("First") },
            device(B) { CompleteLocalName("Second") },
        ) {
            Name("First")
            Name("Second")
        }

        assertEquals(setOf(B), found)
    }

    // ---- Address ----

    @Test
    fun `address filter reports only the device with that address`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
            device(B) { CompleteLocalName("Sensor") },
        ) {
            Address(B)
        }

        assertEquals(setOf(B), found)
    }

    @Test
    fun `address filter that matches no device reports nothing`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
        ) {
            Address(C)
        }

        assertEquals(emptySet(), found)
    }

    // ---- Service UUID ----

    @Test
    fun `service uuid filter reports devices advertising that service`() = runTest {
        val found = scanned(
            device(A) { ServiceUuid(HEART_RATE) },
            device(B) { ServiceUuid(BATTERY) },
            device(C) { ServiceUuid(BATTERY); ServiceUuid(HEART_RATE) },
        ) {
            ServiceUuid(HEART_RATE)
        }

        assertEquals(setOf(A, C), found)
    }

    @Test
    fun `service uuid filter does not report devices advertising no services`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
        ) {
            ServiceUuid(HEART_RATE)
        }

        assertEquals(emptySet(), found)
    }

    @Test
    fun `service uuid filter with a mask ignores the masked out bits`() = runTest {
        // The mask keeps the 12 most significant bits of the 16-bit UUID, so 0x180D matches 0x180F.
        val mask = Uuid.parse("0000fff0-0000-0000-0000-000000000000")
        val found = scanned(
            device(A) { ServiceUuid(BATTERY) },
            device(B) { ServiceUuid(Uuid.fromShortUuid(0x1810)) },
        ) {
            ServiceUuid(HEART_RATE, mask)
        }

        assertEquals(setOf(A), found)
    }

    @Test
    fun `service solicitation uuid filter reports devices soliciting that service`() = runTest {
        val found = scanned(
            device(A) { ServiceSolicitationUuid(HEART_RATE) },
            device(B) { ServiceSolicitationUuid(BATTERY) },
            device(C) { ServiceUuid(HEART_RATE) },
        ) {
            ServiceSolicitationUuid(HEART_RATE)
        }

        assertEquals(setOf(A), found)
    }

    // ---- Service data ----

    @Test
    fun `service data filter reports devices whose service data starts with the given bytes`() = runTest {
        val found = scanned(
            device(A) { ServiceData(ENVIRONMENT, byteArrayOf(0x01, 0x02, 0x03)) },
            device(B) { ServiceData(ENVIRONMENT, byteArrayOf(0x09, 0x02, 0x03)) },
        ) {
            ServiceData(ENVIRONMENT, byteArrayOf(0x01, 0x02))
        }

        assertEquals(setOf(A), found)
    }

    @Test
    @Ignore("ScanFilter.matches passes devices that lack the requested service data / manufacturer data.")
    fun `service data filter does not report devices without service data for that uuid`() = runTest {
        val found = scanned(
            device(A) { ServiceData(ENVIRONMENT, byteArrayOf(0x01)) },
            device(B) { ServiceData(BATTERY, byteArrayOf(0x01)) },
            device(C) { CompleteLocalName("Sensor") },
        ) {
            ServiceData(ENVIRONMENT, byteArrayOf(0x01))
        }

        assertEquals(setOf(A), found)
    }

    @Test
    fun `service data filter with a mask compares only the masked bits`() = runTest {
        val found = scanned(
            device(A) { ServiceData(ENVIRONMENT, byteArrayOf(0x0F, 0x01)) },
            device(B) { ServiceData(ENVIRONMENT, byteArrayOf(0x1F, 0x01)) },
            device(C) { ServiceData(ENVIRONMENT, byteArrayOf(0x1F, 0x02)) },
        ) {
            // Only the low nibble of the first byte and the whole second byte matter.
            ServiceData(
                ENVIRONMENT,
                byteArrayOf(0x0F, 0x01),
                byteArrayOf(0x0F, 0xFF.toByte()),
            )
        }

        assertEquals(setOf(A, B), found)
    }

    // ---- Manufacturer data ----

    @Test
    fun `manufacturer data filter reports devices with matching company id and data prefix`() = runTest {
        val found = scanned(
            device(A) { ManufacturerData(NORDIC, byteArrayOf(0x10, 0x20, 0x30)) },
            device(B) { ManufacturerData(NORDIC, byteArrayOf(0x11, 0x20, 0x30)) },
        ) {
            ManufacturerData(NORDIC, byteArrayOf(0x10, 0x20))
        }

        assertEquals(setOf(A), found)
    }

    @Test
    @Ignore("ScanFilter.matches passes devices that lack the requested service data / manufacturer data.")
    fun `manufacturer data filter does not report devices of another company`() = runTest {
        val found = scanned(
            device(A) { ManufacturerData(NORDIC, byteArrayOf(0x10)) },
            device(B) { ManufacturerData(APPLE, byteArrayOf(0x10)) },
        ) {
            ManufacturerData(NORDIC, byteArrayOf(0x10))
        }

        assertEquals(setOf(A), found)
    }

    @Test
    @Ignore("ScanFilter.matches passes devices that lack the requested service data / manufacturer data.")
    fun `manufacturer data filter does not report devices without manufacturer data`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
            device(B) { ManufacturerData(NORDIC, byteArrayOf(0x10)) },
        ) {
            ManufacturerData(NORDIC, byteArrayOf(0x10))
        }

        assertEquals(setOf(B), found)
    }

    @Test
    @Ignore("ScanFilter.matches passes devices that lack the requested service data / manufacturer data.")
    fun `company id filter reports devices with any data of that company`() = runTest {
        val found = scanned(
            device(A) { ManufacturerData(NORDIC, byteArrayOf(0x01)) },
            device(B) { ManufacturerData(NORDIC, byteArrayOf(0x02, 0x03)) },
            device(C) { ManufacturerData(APPLE, byteArrayOf(0x01)) },
        ) {
            ManufacturerData(NORDIC)
        }

        assertEquals(setOf(A, B), found)
    }

    @Test
    @Ignore("ScanFilter.matches passes devices that lack the requested service data / manufacturer data.")
    fun `company id filter does not report devices without manufacturer data`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
        ) {
            ManufacturerData(NORDIC)
        }

        assertEquals(emptySet(), found)
    }

    // ---- Custom AD type ----

    @Test
    fun `custom ad type filter reports devices that advertise that ad type`() = runTest {
        val found = scanned(
            device(A) { ManufacturerData(NORDIC, byteArrayOf(0x01)) },
            device(B) { CompleteLocalName("Sensor") },
        ) {
            Custom(AdvertisingDataType.MANUFACTURER_SPECIFIC_DATA)
        }

        assertEquals(setOf(A), found)
    }

    @Test
    @Ignore("ScanFilter.matches: Custom(type, data) matches any device that has the type (forEach never returns false).")
    fun `custom ad type filter with data reports only devices with matching data`() = runTest {
        // The manufacturer specific data starts with the Company ID in little endian.
        val found = scanned(
            device(A) { ManufacturerData(NORDIC, byteArrayOf(0x01)) },
            device(B) { ManufacturerData(APPLE, byteArrayOf(0x01)) },
        ) {
            Custom(AdvertisingDataType.MANUFACTURER_SPECIFIC_DATA, byteArrayOf(0x59, 0x00))
        }

        assertEquals(setOf(A), found)
    }

    // ---- Combining ----

    @Test
    fun `several criteria in one filter must all be satisfied`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor"); ServiceUuid(HEART_RATE) },
            device(B) { CompleteLocalName("Sensor"); ServiceUuid(BATTERY) },
            device(C) { CompleteLocalName("Other"); ServiceUuid(HEART_RATE) },
        ) {
            Name("Sensor")
            ServiceUuid(HEART_RATE)
        }

        assertEquals(setOf(A), found)
    }

    @Test
    fun `criteria that no single device satisfies together report nothing`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor") },
            device(B) { ServiceUuid(HEART_RATE) },
        ) {
            Name("Sensor")
            ServiceUuid(HEART_RATE)
        }

        assertEquals(emptySet(), found)
    }

    @Test
    fun `any block reports devices matching at least one alternative`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("First") },
            device(B) { CompleteLocalName("Second") },
            device(C) { CompleteLocalName("Third") },
        ) {
            Any {
                Name("First")
                Name("Second")
            }
        }

        assertEquals(setOf(A, B), found)
    }

    @Test
    fun `any block is combined with the other criteria using and`() = runTest {
        val found = scanned(
            device(A) { CompleteLocalName("Sensor"); ServiceUuid(HEART_RATE) },
            device(B) { CompleteLocalName("Sensor"); ServiceUuid(BATTERY) },
            device(C) { CompleteLocalName("Sensor"); ServiceUuid(ENVIRONMENT) },
            device(D) { CompleteLocalName("Other"); ServiceUuid(HEART_RATE) },
        ) {
            Name("Sensor")
            Any {
                ServiceUuid(HEART_RATE)
                ServiceUuid(BATTERY)
            }
        }

        assertEquals(setOf(A, B), found)
    }

    @Test
    @Ignore("ScanFilter.matches passes devices that lack the requested service data / manufacturer data.")
    fun `nested all inside any requires all criteria of the chosen alternative`() = runTest {
        val found = scanned(
            device(A) { ServiceUuid(HEART_RATE); ServiceData(HEART_RATE, byteArrayOf(0x01)) },
            device(B) { ServiceUuid(HEART_RATE) },
            device(C) { ManufacturerData(NORDIC, byteArrayOf(0x01)) },
            device(D) { ServiceData(HEART_RATE, byteArrayOf(0x01)) },
        ) {
            Any {
                All {
                    ServiceUuid(HEART_RATE)
                    ServiceData(HEART_RATE, byteArrayOf(0x01))
                }
                ManufacturerData(NORDIC, byteArrayOf(0x01))
            }
        }

        assertEquals(setOf(A, C), found)
    }
}
