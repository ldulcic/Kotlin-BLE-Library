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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.environment.android.mock.LatestApi
import com.google.common.truth.Truth
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ADDRESS = "CA:FE:00:00:00:01"
private const val NAME = "Mock Device"

/** Real-time guard for tests running on real dispatchers: failure mode is a hang. */
private val REAL_TIME_GUARD = 5.seconds

/**
 * Tests verifying that the mock central manager can be driven by the standard Kotlin
 * coroutine test infrastructure:
 * - the whole simulation can run on a test scheduler (virtual time, `runTest`),
 * - a single-threaded dispatcher does not deadlock the connection handshake,
 * - state transitions are observable deterministically, without polling.
 */
class MockCentralManagerConnectionTest {

    private fun connectableSpec(
        advertisingInterval: Duration = 1.seconds,
    ): PeripheralSpec<String> =
        PeripheralSpec.simulatePeripheral(ADDRESS) {
            allowForRetrieval()
            connectable(
                name = NAME,
                eventHandler = object : PeripheralSpecEventHandler {},
                services = {},
            )
            advertising(
                parameters = LegacyAdvertisingSetParameters(
                    connectable = true,
                    interval = advertisingInterval,
                ),
            ) {
                CompleteLocalName(NAME)
            }
        }

    @Test
    fun `connect completes under virtual time in runTest`() = runTest {
        val manager = CentralManager.Factory.mock(LatestApi(), backgroundScope) {
            // Scan result timestamps follow the virtual clock as well.
            testScheduler.currentTime
        }
        manager.simulatePeripherals(listOf(connectableSpec()))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        // A consumer-style timeout: counts on the virtual clock, just like the
        // simulated 1 s advertising interval. No real-time waits.
        withTimeout(10.seconds) {
            manager.connect(peripheral)
        }

        Truth.assertThat(peripheral.state.value).isEqualTo(ConnectionState.Connected)
        manager.close()
    }

    @Test
    fun `mock on real dispatchers cannot be awaited under virtual time`() = runTest {
        // Documents the failure mode this suite guards against: when the simulation runs
        // on real dispatchers, the test scheduler sees only an idle queue and fast-forwards
        // the virtual clock past the timeout while the real-time handshake has barely begun.
        // The supported pattern is passing a test-scheduler-backed scope, as in the test above.
        val realScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val manager = CentralManager.Factory.mock(LatestApi(), realScope)
            manager.simulatePeripherals(listOf(connectableSpec()))
            val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

            assertFailsWith<TimeoutCancellationException> {
                withTimeout(10.seconds) {
                    manager.connect(peripheral)
                }
            }
            manager.close()
        } finally {
            realScope.cancel()
        }
    }

    @Test
    fun `connect completes on a single threaded dispatcher`() {
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
        val managerScope = CoroutineScope(dispatcher + SupervisorJob())
        try {
            runBlocking(dispatcher) {
                val manager = CentralManager.Factory.mock(LatestApi(), managerScope)
                manager.simulatePeripherals(
                    listOf(connectableSpec(advertisingInterval = 50.milliseconds))
                )
                val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

                withTimeout(REAL_TIME_GUARD) {
                    manager.connect(peripheral)
                }

                Truth.assertThat(peripheral.state.value).isEqualTo(ConnectionState.Connected)
                manager.close()
            }
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun `connect called from the scan collector completes on a single threaded dispatcher`() {
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
        val managerScope = CoroutineScope(dispatcher + SupervisorJob())
        try {
            runBlocking(dispatcher) {
                val manager = CentralManager.Factory.mock(LatestApi(), managerScope)
                manager.simulatePeripherals(
                    listOf(connectableSpec(advertisingInterval = 50.milliseconds))
                )

                val result = withTimeout(REAL_TIME_GUARD) {
                    manager.scan(timeout = 30.seconds) {}
                        // Connect as soon as the device is found, while the scan flow is
                        // still being collected — a typical "scan, then connect" consumer.
                        .onEach { scanResult ->
                            if (scanResult.peripheral.identifier == ADDRESS) {
                                manager.connect(scanResult.peripheral)
                            }
                        }
                        .first { it.peripheral.identifier == ADDRESS }
                }

                Truth.assertThat(result.peripheral.state.value).isEqualTo(ConnectionState.Connected)
                manager.close()
            }
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun `state transitions are observable without polling`() = runTest {
        val manager = CentralManager.Factory.mock(LatestApi(), backgroundScope)
        manager.simulatePeripherals(listOf(connectableSpec()))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        val states = mutableListOf<ConnectionState>()
        backgroundScope.launch { peripheral.state.collect { states += it } }

        launch { manager.connect(peripheral) }
        val state = peripheral.state.first { it == ConnectionState.Connected }
        runCurrent()

        Truth.assertThat(state).isEqualTo(ConnectionState.Connected)
        // Connecting must be observed, and before Connected.
        Truth.assertThat(states)
            .containsAtLeast(ConnectionState.Connecting, ConnectionState.Connected)
            .inOrder()
        manager.close()
    }

    @Test
    fun `spec simulation timers run on virtual time`() = runTest {
        val manager = CentralManager.Factory.mock(LatestApi(), backgroundScope)
        val spec = connectableSpec()
        manager.simulatePeripherals(listOf(spec))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)
        Truth.assertThat(peripheral.state.value).isEqualTo(ConnectionState.Connected)

        // Moving the device out of range drops the link after the supervision timeout,
        // which defaults to 400 × 10 ms = 4 s.
        spec.simulateProximityChange(Proximity.OUT_OF_RANGE)

        // The link is not lost before the supervision timeout...
        advanceTimeBy(3.seconds)
        runCurrent()
        Truth.assertThat(peripheral.state.value).isEqualTo(ConnectionState.Connected)

        // ...and is reported lost right after it — in virtual time, no real waits.
        val state = withTimeout(2.seconds) {
            peripheral.state.first { it is ConnectionState.Disconnected }
        }
        Truth.assertThat(state).isInstanceOf(ConnectionState.Disconnected::class.java)
        Truth.assertThat((state as ConnectionState.Disconnected).reason)
            .isEqualTo(ConnectionState.Disconnected.Reason.LinkLoss)
        manager.close()
    }

    @Test
    fun `tearDownSimulation cancels pending simulation work`() = runTest {
        val manager = CentralManager.Factory.mock(LatestApi(), backgroundScope)
        val spec = connectableSpec()
        manager.simulatePeripherals(listOf(spec))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)
        Truth.assertThat(peripheral.state.value).isEqualTo(ConnectionState.Connected)

        // Schedule a link loss (due in 4 s) and tear the simulation down before it fires.
        spec.simulateProximityChange(Proximity.OUT_OF_RANGE)
        manager.tearDownSimulation()

        // The pending link-loss timer was cancelled together with the simulation:
        // no disconnection event may arrive afterwards.
        advanceTimeBy(10.seconds)
        runCurrent()
        Truth.assertThat(peripheral.state.value).isEqualTo(ConnectionState.Connected)
        manager.close()
    }

    @Test
    fun `connect started after the device disappeared does not use stale advertisements`() = runTest {
        val manager = CentralManager.Factory.mock(LatestApi(), backgroundScope)
        val spec = connectableSpec()
        manager.simulatePeripherals(listOf(spec))

        // Let the device advertise a few times with nobody listening, then disappear.
        advanceTimeBy(2500.milliseconds)
        spec.simulateProximityChange(Proximity.OUT_OF_RANGE)

        // A connection attempt started now must not be able to respond to any of the
        // past advertisements — advertising is not replayed to late subscribers.
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(5.seconds) {
                manager.connect(peripheral)
            }
        }
        Truth.assertThat(peripheral.state.value)
            .isInstanceOf(ConnectionState.Disconnected::class.java)
        manager.close()
    }
}
