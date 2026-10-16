/*
 * Copyright (c) 2024, Nordic Semiconductor
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.CentralManager.ConnectionOptions
import no.nordicsemi.kotlin.ble.client.android.exception.BondingFailedException
import no.nordicsemi.kotlin.ble.client.mock.AddressType
import no.nordicsemi.kotlin.ble.client.mock.BondResult
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.core.BondState
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.exception.ManagerClosedException
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ADDRESS = "CA:FE:00:00:00:01"
private const val OTHER_ADDRESS = "CA:FE:00:00:00:02"

// Two most significant bits of the address are 01: Resolvable Private Address.
private const val RPA_ADDRESS = "4A:11:22:33:44:55"

/**
 * Tests of bond information: the bond state, creating and removing bonds and retrieving bonded
 * peripherals.
 */
class ConnectionBondingTest {

    /** A peripheral that was connected before, and so is known by the phone, and may be bonded. */
    private fun spec(
        address: String = ADDRESS,
        addressType: AddressType = AddressType.PUBLIC,
        isBonded: Boolean,
        peripheralHasBond: Boolean = isBonded,
        eventHandler: PeripheralSpecEventHandler = object : PeripheralSpecEventHandler {},
    ): PeripheralSpec<String> = PeripheralSpec.simulatePeripheral(
        identifier = address,
        addressType = addressType,
    ) {
        connectable(
            name = "Device",
            isBonded = isBonded,
            peripheralHasBond = peripheralHasBond,
            eventHandler = eventHandler,
            cachedServices = {},
        )
        advertising(
            parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
        ) {
            CompleteLocalName("Device")
        }
    }

    private fun TestScope.manager(
        vararg specs: PeripheralSpec<String>,
        environment: MockAndroidEnvironment = MockAndroidEnvironment.Api31(),
    ): MockCentralManager = CentralManager.mock(environment = environment, scope = backgroundScope).apply {
        simulatePeripherals(specs.toList())
    }

    @Test
    fun `bonded peripheral has bond information`() = runTest {
        val manager = manager(spec(isBonded = true))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        assertEquals(BondState.BONDED, peripheral.bondState.value)
        assertTrue(peripheral.hasBondInformation)
    }

    @Test
    fun `not bonded peripheral has no bond information`() = runTest {
        val manager = manager(spec(isBonded = false))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        assertEquals(BondState.NONE, peripheral.bondState.value)
        assertFalse(peripheral.hasBondInformation)
    }

    @Test
    fun `bond information is kept after disconnecting`() = runTest {
        val manager = manager(spec(isBonded = true))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.disconnect()

        assertEquals(BondState.BONDED, peripheral.bondState.value)
    }

    @Test
    fun `bonded peripherals are returned`() = runTest {
        val manager = manager(
            spec(address = ADDRESS, isBonded = true),
            spec(address = OTHER_ADDRESS, isBonded = false),
        )

        val bonded = manager.getBondedPeripherals()

        assertEquals(listOf(ADDRESS), bonded.map { it.address })
    }

    @Test
    fun `no peripherals are returned when none is bonded`() = runTest {
        val manager = manager(spec(isBonded = false))

        assertTrue(manager.getBondedPeripherals().isEmpty())
    }

    @Test
    fun `bonded peripheral is the same instance as the one retrieved by id`() = runTest {
        val manager = manager(spec(isBonded = true))

        val bonded = manager.getBondedPeripherals().single()

        assertSame(bonded, manager.getPeripheralById(ADDRESS))
    }

    @Test
    fun `bonded peripheral can be connected without scanning`() = runTest {
        // Bonded devices using random addresses are known by the phone.
        val manager = manager(spec(addressType = AddressType.RANDOM_STATIC, isBonded = true))
        val peripheral = manager.getBondedPeripherals().single()

        manager.connect(peripheral, ConnectionOptions.Direct(timeout = 5.seconds))

        assertEquals(ConnectionState.Connected, peripheral.state.value)
    }

    @Test
    fun `auto connect to a bonded device with resolvable private address is supported`() = runTest {
        val manager = manager(spec(address = RPA_ADDRESS, addressType = AddressType.RANDOM_PRIVATE_RESOLVABLE, isBonded = true))
        val peripheral = manager.getBondedPeripherals().single()

        manager.connect(peripheral, ConnectionOptions.AutoConnect())

        assertEquals(ConnectionState.Connected, peripheral.state.value)
    }

    @Test
    fun `getting bonded peripherals on a closed manager throws`() = runTest {
        val manager = manager(spec(isBonded = true))

        manager.close()

        assertFailsWith<ManagerClosedException> { manager.getBondedPeripherals() }
    }

    @Test
    fun `getting bonded peripherals without the connect permission throws SecurityException`() = runTest {
        val environment = MockAndroidEnvironment.Api31(isBluetoothConnectPermissionGranted = false)
        val manager = manager(spec(isBonded = true), environment = environment)

        assertFailsWith<SecurityException> { manager.getBondedPeripherals() }
    }

    @Test
    fun `creating a bond bonds the peripheral`() = runTest {
        val manager = manager(spec(isBonded = false))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.createBond()

        assertEquals(BondState.BONDED, peripheral.bondState.value)
        assertTrue(peripheral.hasBondInformation)
    }

    @Test
    fun `created bond is returned with bonded peripherals`() = runTest {
        val manager = manager(spec(isBonded = false))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.createBond()

        assertEquals(listOf(ADDRESS), manager.getBondedPeripherals().map { it.address })
    }

    @Test
    fun `creating a bond with an already bonded peripheral does nothing`() = runTest {
        val manager = manager(spec(isBonded = true))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.createBond()

        assertEquals(BondState.BONDED, peripheral.bondState.value)
    }

    @Test
    fun `removing a bond removes the bond information`() = runTest {
        val manager = manager(spec(isBonded = true))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        peripheral.removeBond()

        assertEquals(BondState.NONE, peripheral.bondState.value)
        assertTrue(manager.getBondedPeripherals().isEmpty())
    }

    @Test
    fun `removing a bond terminates the connection`() = runTest {
        val manager = manager(spec(isBonded = true))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.removeBond()
        runCurrent()

        assertIs<ConnectionState.Disconnected>(peripheral.state.value)
    }

    @Test
    fun `removing a bond that does not exist does nothing`() = runTest {
        val manager = manager(spec(isBonded = false))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        peripheral.removeBond()

        assertEquals(BondState.NONE, peripheral.bondState.value)
    }

    @Test
    fun `removing a bond on Android 17 requires the privileged permission`() = runTest {
        val manager = manager(spec(isBonded = true), environment = MockAndroidEnvironment.Api37())
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        assertFailsWith<SecurityException> { peripheral.removeBond() }
        assertEquals(BondState.BONDED, peripheral.bondState.value)
    }

    @Test
    fun `removing a bond on Android 17 is possible with the privileged permission`() = runTest {
        val environment = MockAndroidEnvironment.Api37(isBluetoothPrivilegedPermissionGranted = true)
        val manager = manager(spec(isBonded = true), environment = environment)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        peripheral.removeBond()

        assertEquals(BondState.NONE, peripheral.bondState.value)
    }

    @Test
    fun `rejected bond request fails creating the bond`() = runTest {
        val rejecting = object : PeripheralSpecEventHandler {
            override fun onBondRequest() = BondResult.Reject()
        }
        val manager = manager(spec(isBonded = false, eventHandler = rejecting))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        assertFailsWith<BondingFailedException> { peripheral.createBond() }

        assertEquals(BondState.NONE, peripheral.bondState.value)
        assertTrue(manager.getBondedPeripherals().isEmpty())
    }

    @Test
    fun `bond request is passed to the event handler`() = runTest {
        var requests = 0
        val counting = object : PeripheralSpecEventHandler {
            override fun onBondRequest(): BondResult = BondResult.Accept.also { requests++ }
        }
        val manager = manager(spec(isBonded = false, eventHandler = counting))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.createBond()

        assertEquals(1, requests)
    }

    @Test
    fun `bond state goes through bonding`() = runTest {
        val manager = manager(spec(isBonded = false))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)
        val states = mutableListOf<BondState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            peripheral.bondState.toList(states)
        }

        peripheral.createBond()

        assertEquals(listOf(BondState.NONE, BondState.BONDING, BondState.BONDED), states)
    }

    @Test
    fun `bond is kept by the spec for other managers`() = runTest {
        val spec = spec(isBonded = false)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)
        peripheral.createBond()

        val otherManager = manager(spec)

        assertEquals(listOf(ADDRESS), otherManager.getBondedPeripherals().map { it.address })
    }

    @Test
    fun `peripheral stores the bond by default`() = runTest {
        assertTrue(spec(isBonded = true).peripheralHasBond)
        assertFalse(spec(isBonded = false).peripheralHasBond)
    }

    @Test
    fun `creating a bond makes the peripheral store it`() = runTest {
        val spec = spec(isBonded = false)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        peripheral.createBond()

        assertTrue(spec.peripheralHasBond)
    }

    @Test
    fun `removing a bond does not make the peripheral forget it`() = runTest {
        val spec = spec(isBonded = true)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        peripheral.removeBond()

        assertTrue(spec.peripheralHasBond)
    }

    @Test
    fun `peripheral that still has the bond rejects bonding`() = runTest {
        var requests = 0
        val counting = object : PeripheralSpecEventHandler {
            override fun onBondRequest(): BondResult = BondResult.Accept.also { requests++ }
        }
        // The phone forgot the bond, but the peripheral did not.
        val spec = spec(isBonded = false, peripheralHasBond = true, eventHandler = counting)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        assertFailsWith<BondingFailedException> { peripheral.createBond() }

        assertEquals(BondState.NONE, peripheral.bondState.value)
        assertEquals(0, requests)
    }

    @Test
    fun `bonding succeeds after the peripheral forgets the bond`() = runTest {
        val spec = spec(isBonded = false, peripheralHasBond = true)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)
        spec.simulatePeripheralBondLoss()

        peripheral.createBond()

        assertEquals(BondState.BONDED, peripheral.bondState.value)
    }

    @Test
    fun `peripheral forgetting the bond does not remove the bond from the phone`() = runTest {
        val spec = spec(isBonded = true)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        spec.simulatePeripheralBondLoss()

        assertFalse(spec.peripheralHasBond)
        assertEquals(BondState.BONDED, peripheral.bondState.value)
    }

    @Test
    fun `simulated bond removal terminates the connection`() = runTest {
        val spec = spec(isBonded = true)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        manager.connect(peripheral)

        spec.simulateBondRemoval()
        runCurrent()

        assertEquals(BondState.NONE, peripheral.bondState.value)
        assertIs<ConnectionState.Disconnected>(peripheral.state.value)
        assertTrue(spec.peripheralHasBond)
    }

    @Test
    fun `simulated bonding bonds the peripheral`() = runTest {
        val spec = spec(isBonded = false)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        spec.simulateBonding()
        runCurrent()

        assertEquals(BondState.BONDED, peripheral.bondState.value)
    }

    private suspend fun TestScope.failedBondingReason(result: BondResult): BondingFailedException.Reason {
        val rejecting = object : PeripheralSpecEventHandler {
            override fun onBondRequest() = result
        }
        val manager = manager(spec(isBonded = false, eventHandler = rejecting))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        return assertFailsWith<BondingFailedException> { peripheral.createBond() }.reason
    }

    @Test
    fun `rejected bonding reports the peripheral rejecting it by default`() = runTest {
        assertEquals(
            BondingFailedException.Reason.AuthenticationRejected,
            failedBondingReason(BondResult.Reject()),
        )
    }

    @Test
    fun `bonding cancelled by the user reports the reason`() = runTest {
        assertEquals(
            BondingFailedException.Reason.AuthenticationCanceled,
            failedBondingReason(BondResult.Reject(BondResult.Reject.Reason.Cancelled)),
        )
    }

    @Test
    fun `bonding with a wrong PIN reports the reason`() = runTest {
        assertEquals(
            BondingFailedException.Reason.AuthenticationFailed,
            failedBondingReason(BondResult.Reject(BondResult.Reject.Reason.AuthenticationFailed)),
        )
    }

    @Test
    fun `bonding that timed out reports the reason`() = runTest {
        assertEquals(
            BondingFailedException.Reason.AuthenticationTimeout,
            failedBondingReason(BondResult.Reject(BondResult.Reject.Reason.Timeout)),
        )
    }

    @Test
    fun `bonding with a peripheral that still has the bond reports it was rejected`() = runTest {
        val manager = manager(spec(isBonded = false, peripheralHasBond = true))
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))

        val exception = assertFailsWith<BondingFailedException> { peripheral.createBond() }

        assertEquals(BondingFailedException.Reason.AuthenticationRejected, exception.reason)
    }

    @Test
    fun `reason of a failed bonding is not kept after bonding succeeds`() = runTest {
        var result: BondResult = BondResult.Reject(BondResult.Reject.Reason.Cancelled)
        val handler = object : PeripheralSpecEventHandler {
            override fun onBondRequest() = result
        }
        val spec = spec(isBonded = false, eventHandler = handler)
        val manager = manager(spec)
        val peripheral = checkNotNull(manager.getPeripheralById(ADDRESS))
        assertFailsWith<BondingFailedException> { peripheral.createBond() }

        result = BondResult.Accept
        peripheral.createBond()

        assertEquals(BondState.BONDED, peripheral.bondState.value)
        assertEquals(null, spec.bondFailureReason)
    }
}

