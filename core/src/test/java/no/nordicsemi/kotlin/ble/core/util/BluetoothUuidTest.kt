package no.nordicsemi.kotlin.ble.core.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class BluetoothUuidTest {

    private val random128 = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    // Base UUID

    @Test
    fun `base UUID is the Bluetooth Base UUID`() {
        assertEquals(
            Uuid.parse("00000000-0000-1000-8000-00805f9b34fb"),
            Uuid.baseUuid
        )
    }

    // fromShortUuid

    @Test
    fun `fromShortUuid expands 16-bit value onto the base UUID`() {
        assertEquals(
            Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb"),
            Uuid.fromShortUuid(0x180F)
        )
    }

    @Test
    fun `fromShortUuid expands 32-bit value onto the base UUID`() {
        assertEquals(
            Uuid.parse("12345678-0000-1000-8000-00805f9b34fb"),
            Uuid.fromShortUuid(0x12345678)
        )
    }

    @Test
    fun `fromShortUuid with zero is the base UUID`() {
        assertEquals(Uuid.baseUuid, Uuid.fromShortUuid(0))
    }

    // Type detection

    @Test
    fun `16-bit UUID is detected as 16-bit and not as 32-bit`() {
        val uuid = Uuid.fromShortUuid(0x180F)
        assertTrue(uuid.is16BitUuid)
        assertFalse(uuid.is32BitUuid)
        assertTrue(uuid.isShortUuid)
    }

    @Test
    fun `32-bit UUID is detected as 32-bit and not as 16-bit`() {
        val uuid = Uuid.fromShortUuid(0x12345678)
        assertTrue(uuid.is32BitUuid)
        assertFalse(uuid.is16BitUuid)
        assertTrue(uuid.isShortUuid)
    }

    @Test
    fun `32-bit UUID with highest bit set is detected as 32-bit`() {
        val uuid = Uuid.fromShortUuid(0x80000001.toInt())
        assertTrue(uuid.is32BitUuid)
        assertFalse(uuid.is16BitUuid)
    }

    @Test
    fun `128-bit UUID is neither 16-bit nor 32-bit`() {
        assertFalse(random128.is16BitUuid)
        assertFalse(random128.is32BitUuid)
        assertFalse(random128.isShortUuid)
    }

    @Test
    fun `UUID with Bluetooth base but modified middle part is not a short UUID`() {
        val uuid = Uuid.parse("0000180f-0001-1000-8000-00805f9b34fb")
        assertFalse(uuid.isShortUuid)
    }

    @Test
    fun `UUID with modified lower half is not a short UUID`() {
        val uuid = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fc")
        assertFalse(uuid.isShortUuid)
    }

    // shortUuid

    @Test
    fun `shortUuid returns the 16-bit value`() {
        assertEquals(0x180F, Uuid.fromShortUuid(0x180F).shortUuid)
    }

    @Test
    fun `shortUuid returns the 32-bit value`() {
        assertEquals(0x12345678, Uuid.fromShortUuid(0x12345678).shortUuid)
    }

    @Test
    fun `shortUuid of 128-bit UUID is null`() {
        assertNull(random128.shortUuid)
    }

    // toShortString

    @Test
    fun `toShortString of 16-bit UUID is 4 uppercase hex digits`() {
        assertEquals("180F", Uuid.fromShortUuid(0x180F).toShortString())
    }

    @Test
    fun `toShortString of 16-bit UUID keeps leading zeros`() {
        assertEquals("0001", Uuid.fromShortUuid(1).toShortString())
    }

    @Test
    fun `toShortString of 32-bit UUID is 8 uppercase hex digits`() {
        assertEquals("12345678", Uuid.fromShortUuid(0x12345678).toShortString())
        assertEquals("00010000", Uuid.fromShortUuid(0x00010000).toShortString())
    }

    @Test
    fun `toShortString of 32-bit UUID with highest bit set is not signed`() {
        assertEquals("FFFFFFFF", Uuid.fromShortUuid(-1).toShortString())
        assertEquals("80000001", Uuid.fromShortUuid(0x80000001.toInt()).toShortString())
    }

    @Test
    fun `toShortString of 128-bit UUID is full uppercase string`() {
        assertEquals("6E400001-B5A3-F393-E0A9-E50E24DCCA9E", random128.toShortString())
    }

    // toShortByteArray

    @Test
    fun `toShortByteArray of 16-bit UUID is 2 bytes little endian`() {
        assertContentEquals(
            byteArrayOf(0x0F, 0x18),
            Uuid.fromShortUuid(0x180F).toShortByteArray()
        )
    }

    @Test
    fun `toShortByteArray of 32-bit UUID is 4 bytes little endian`() {
        assertContentEquals(
            byteArrayOf(0x78, 0x56, 0x34, 0x12),
            Uuid.fromShortUuid(0x12345678).toShortByteArray()
        )
    }

    @Test
    fun `toShortByteArray of 128-bit UUID is 16 bytes little endian`() {
        assertContentEquals(
            random128.toByteArray().reversedArray(),
            random128.toShortByteArray()
        )
    }

    // fromBytes

    @Test
    fun `fromBytes with 2 bytes creates 16-bit UUID from little endian value`() {
        assertEquals(
            Uuid.fromShortUuid(0x180F),
            Uuid.fromBytes(byteArrayOf(0x0F, 0x18))
        )
    }

    @Test
    fun `fromBytes with 4 bytes creates 32-bit UUID from little endian value`() {
        assertEquals(
            Uuid.fromShortUuid(0x12345678),
            Uuid.fromBytes(byteArrayOf(0x78, 0x56, 0x34, 0x12))
        )
    }

    @Test
    fun `fromBytes with 4 bytes and highest bit set creates 32-bit UUID`() {
        val uuid = Uuid.fromBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(Uuid.parse("ffffffff-0000-1000-8000-00805f9b34fb"), uuid)
    }

    @Test
    fun `fromBytes with 16 bytes round trips with toShortByteArray`() {
        assertEquals(random128, Uuid.fromBytes(random128.toShortByteArray()))
    }

    @Test
    fun `fromBytes respects offset and length`() {
        val bytes = byteArrayOf(0x55, 0x55, 0x0F, 0x18, 0x55)
        assertEquals(Uuid.fromShortUuid(0x180F), Uuid.fromBytes(bytes, 2, 2))
    }

    @Test
    fun `fromBytes with 16 bytes respects offset`() {
        val bytes = byteArrayOf(0x55) + random128.toShortByteArray() + byteArrayOf(0x55)
        assertEquals(random128, Uuid.fromBytes(bytes, 1, 16))
    }

    @Test
    fun `fromBytes with unsupported length throws`() {
        assertFailsWith<IllegalArgumentException> { Uuid.fromBytes(byteArrayOf(1, 2, 3)) }
        assertFailsWith<IllegalArgumentException> { Uuid.fromBytes(byteArrayOf()) }
    }

    @Test
    fun `fromBytes with array too short for given offset throws`() {
        assertFailsWith<IllegalArgumentException> { Uuid.fromBytes(byteArrayOf(1, 2, 3), 2, 2) }
    }

    // and

    @Test
    fun `and applies mask to both halves`() {
        val mask = Uuid.parse("ffffffff-ffff-0000-0000-000000000000")
        assertEquals(
            Uuid.parse("6e400001-b5a3-0000-0000-000000000000"),
            random128 and mask
        )
    }

    @Test
    fun `and with all ones is identity`() {
        val ones = Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")
        assertEquals(random128, random128 and ones)
    }
}
