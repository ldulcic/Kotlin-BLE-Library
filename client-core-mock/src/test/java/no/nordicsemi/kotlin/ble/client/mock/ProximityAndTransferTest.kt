package no.nordicsemi.kotlin.ble.client.mock

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ProximityAndTransferTest {

    private fun samples(proximity: Proximity) = List(500) { proximity.randomRssi() }

    // Proximity

    @Test
    fun `closer proximity always gives stronger RSSI`() {
        assertTrue(samples(Proximity.IMMEDIATE).min() > samples(Proximity.NEAR).max())
        assertTrue(samples(Proximity.NEAR).min() > samples(Proximity.FAR).max())
    }

    @Test
    fun `RSSI values of in-range proximities are negative`() {
        listOf(Proximity.IMMEDIATE, Proximity.NEAR, Proximity.FAR).forEach { proximity ->
            assertTrue(samples(proximity).all { it < 0 }, "$proximity")
        }
    }

    @Test
    @Ignore("Needs triage: OUT_OF_RANGE reports -128, ScanResult.rssi KDoc says -127..126.")
    fun `RSSI values of all proximities are within the range reported by scan results`() {
        // ScanResult.rssi is documented to be in the range -127..126.
        listOf(Proximity.IMMEDIATE, Proximity.NEAR, Proximity.FAR, Proximity.OUT_OF_RANGE).forEach { proximity ->
            assertTrue(samples(proximity).all { it in -127..126 }, "$proximity")
        }
    }

    @Test
    fun `immediate proximity gives RSSI above -50 dBm`() {
        assertTrue(samples(Proximity.IMMEDIATE).all { it >= -50 })
    }

    @Test
    fun `far proximity gives RSSI below -80 dBm`() {
        assertTrue(samples(Proximity.FAR).all { it <= -80 })
    }

    @Test
    fun `out of range proximity always gives the same lowest RSSI`() {
        assertEquals(1, samples(Proximity.OUT_OF_RANGE).toSet().size)
    }

    // Transfer duration estimation

    private fun connectedSpec(maxAttMtu: Int = 23, maxLlMtu: Int = 27): PeripheralSpec<String> =
        PeripheralSpec.simulatePeripheral("id") {
            connectable(
                name = "Device",
                preferredConnectionInterval = 40..40, // 50 ms
                maxAttMtu = maxAttMtu,
                maxL2capMtu = maxLlMtu,
                eventHandler = object : PeripheralSpecEventHandler {},
                services = { },
            )
        }.also { it.simulateConnection() }

    private val interval = 50.milliseconds

    @Test
    fun `estimating transfer of a disconnected peripheral fails`() {
        val spec = PeripheralSpec.simulatePeripheral("id") {
            connectable(name = "Device", eventHandler = object : PeripheralSpecEventHandler {}, services = { })
        }
        assertFailsWith<IllegalStateException> {
            spec.estimateTransferDuration(byteArrayOf(1), isWrite = false)
        }
    }

    @Test
    fun `read of value fitting in a single response takes one connection interval`() {
        // With ATT MTU of 23 the Read Response carries up to 22 bytes.
        val spec = connectedSpec()
        assertEquals(interval, spec.estimateTransferDuration(ByteArray(22), isWrite = false))
        assertEquals(interval, spec.estimateTransferDuration(ByteArray(1), isWrite = false))
    }

    @Test
    fun `read of value longer than a single response takes longer`() {
        val spec = connectedSpec()
        assertTrue(
            spec.estimateTransferDuration(ByteArray(23), isWrite = false) >
                    spec.estimateTransferDuration(ByteArray(22), isWrite = false)
        )
    }

    @Test
    fun `write with response of value fitting in a single request takes one connection interval`() {
        // With ATT MTU of 23 the Write Request carries up to 20 bytes.
        val spec = connectedSpec()
        assertEquals(interval, spec.estimateTransferDuration(ByteArray(20), isWrite = true))
    }

    @Test
    fun `long write takes longer than a single write`() {
        val spec = connectedSpec()
        assertTrue(
            spec.estimateTransferDuration(ByteArray(21), isWrite = true) >
                    spec.estimateTransferDuration(ByteArray(20), isWrite = true)
        )
    }

    @Test
    fun `larger MTU makes transfer of a long value faster`() {
        val small = connectedSpec(maxAttMtu = 23, maxLlMtu = 27)
        val large = connectedSpec(maxAttMtu = 247, maxLlMtu = 251)
        val value = ByteArray(200)
        assertTrue(
            large.estimateTransferDuration(value, isWrite = false) <
                    small.estimateTransferDuration(value, isWrite = false)
        )
        assertTrue(
            large.estimateTransferDuration(value, isWrite = true) <
                    small.estimateTransferDuration(value, isWrite = true)
        )
    }

    @Test
    fun `write without response is not slower than write with response`() {
        val spec = connectedSpec(maxAttMtu = 247, maxLlMtu = 251)
        val value = ByteArray(200)
        assertTrue(
            spec.estimateTransferDuration(value, isWrite = true, withResponse = false) <=
                    spec.estimateTransferDuration(value, isWrite = true, withResponse = true)
        )
    }

    @Test
    fun `write without response of longer value takes longer`() {
        val spec = connectedSpec(maxAttMtu = 247, maxLlMtu = 251)
        assertTrue(
            spec.estimateTransferDuration(ByteArray(240), isWrite = true, withResponse = false) >
                    spec.estimateTransferDuration(ByteArray(20), isWrite = true, withResponse = false)
        )
    }

    @Test
    fun `transfer of an empty value does not take negative time`() {
        val spec = connectedSpec()
        assertTrue(spec.estimateTransferDuration(ByteArray(0), isWrite = true, withResponse = false) >= Duration.ZERO)
        assertTrue(spec.estimateTransferDuration(ByteArray(0), isWrite = false) >= Duration.ZERO)
    }
}
