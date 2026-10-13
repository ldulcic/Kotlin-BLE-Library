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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests of the timestamps of scan results reported by the mock central manager.
 */
class ScanTimestampTest {

    @Test
    fun `scan result timestamps come from the given clock`() = runTest {
        // A clock following the virtual time of the test.
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        val manager = CentralManager.mock(scope = backgroundScope, clock = clock)

        val timestamps = mutableListOf<Long>()
        val receivedAt = mutableListOf<Long>()
        backgroundScope.launch {
            manager.scan().collect {
                timestamps += it.timestamp.toEpochMilliseconds()
                receivedAt += testScheduler.currentTime
            }
        }
        runCurrent()

        // A device starts advertising every second.
        manager.simulatePeripherals(listOf(
            PeripheralSpec.simulatePeripheral("CA:FE:00:00:00:01") {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(
                        connectable = false,
                        interval = 1.seconds,
                    ),
                ) {
                    CompleteLocalName("Sensor")
                }
            }
        ))
        advanceTimeBy(2500.milliseconds)
        runCurrent()

        // Each result is stamped with the virtual time at which the device advertised.
        assertEquals(3, timestamps.size)
        assertEquals(receivedAt, timestamps)
    }
}
