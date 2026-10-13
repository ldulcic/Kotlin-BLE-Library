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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of the random delay that the mock advertiser adds to each advertising event,
 * like real devices do.
 */
class AdvertisingDelayTest {

    private fun sensor(address: String, names: List<String> = listOf("Sensor")) =
        PeripheralSpec.simulatePeripheral(address) {
            names.forEach { name ->
                advertising(
                    parameters = LegacyAdvertisingSetParameters(
                        connectable = false,
                        interval = 100.milliseconds,
                    ),
                ) {
                    CompleteLocalName(name)
                }
            }
        }

    @Test
    fun `advertising events come 0 to 10 ms later than the interval`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        manager.simulatePeripherals(listOf(sensor("CA:FE:00:00:00:01")))

        val times = mutableListOf<Long>()
        backgroundScope.launch {
            manager.scan().collect { times += testScheduler.currentTime }
        }
        advanceTimeBy(10.seconds)
        runCurrent()

        val intervals = times.zipWithNext { previous, next -> next - previous }
        assertTrue(intervals.all { it in 100..110 }, "Intervals: $intervals")
        assertTrue(intervals.distinct().size > 1, "Intervals: $intervals")
    }

    @Test
    fun `devices with the same interval advertise at different times`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        manager.simulatePeripherals(listOf(sensor("CA:FE:00:00:00:01"), sensor("CA:FE:00:00:00:02")))

        val times = mutableMapOf<String, MutableList<Long>>()
        backgroundScope.launch {
            manager.scan().collect { result ->
                times.getOrPut(result.peripheral.identifier) { mutableListOf() } += testScheduler.currentTime
            }
        }
        advanceTimeBy(2.seconds)
        runCurrent()

        assertAdvertisedAtDifferentTimes(times)
    }

    @Test
    fun `advertising sets of one device advertise at different times`() = runTest {
        val manager = CentralManager.mock(scope = backgroundScope)
        manager.simulatePeripherals(listOf(sensor("CA:FE:00:00:00:01", names = listOf("A", "B"))))

        // The two advertising sets are told apart by their advertising data.
        val times = mutableMapOf<String, MutableList<Long>>()
        backgroundScope.launch {
            manager.scan().collect { result ->
                times.getOrPut(result.advertisingData.raw.contentToString()) { mutableListOf() } += testScheduler.currentTime
            }
        }
        advanceTimeBy(2.seconds)
        runCurrent()

        assertAdvertisedAtDifferentTimes(times)
    }

    /** Asserts that there are two advertisers, both advertising all the time, but at different times. */
    private fun assertAdvertisedAtDifferentTimes(times: Map<String, List<Long>>) {
        assertEquals(2, times.size, "Times: $times")
        val (first, second) = times.values.toList()
        assertTrue(first.size > 10 && second.size > 10, "Times: $times")
        assertNotEquals(first, second)
    }
}
