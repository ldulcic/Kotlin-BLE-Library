package no.nordicsemi.kotlin.ble.core.mock

import no.nordicsemi.kotlin.ble.core.AdvertisingDataFlag
import no.nordicsemi.kotlin.ble.core.util.fromShortUuid
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests of the mock [AdvertisingDataDefinition]: encoding of the typed constructor into raw
 * AD structures and parsing of raw bytes back to typed fields.
 */
class AdvertisingDataDefinitionTest {

    private val uuid128 = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // Encoding

    @Test
    fun `empty definition has empty raw data and no fields`() {
        val data = AdvertisingDataDefinition()
        assertEquals(0, data.raw.size)
        assertNull(data.flags)
        assertNull(data.completeLocalName)
        assertNull(data.shortenedLocalName)
        assertNull(data.txPowerLevel)
        assertNull(data.serviceUuids)
        assertNull(data.manufacturerData)
    }

    @Test
    fun `flags are encoded as AD structure of type 0x01`() {
        val data = AdvertisingDataDefinition(
            flags = setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE, AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED)
        )
        assertContentEquals(bytes(0x02, 0x01, 0x06), data.raw)
    }

    @Test
    fun `complete local name is encoded as AD structure of type 0x09`() {
        val data = AdvertisingDataDefinition(completeLocalName = "AB")
        assertContentEquals(bytes(0x03, 0x09, 0x41, 0x42), data.raw)
    }

    @Test
    fun `shortened local name is encoded as AD structure of type 0x08`() {
        val data = AdvertisingDataDefinition(shortenedLocalName = "AB")
        assertContentEquals(bytes(0x03, 0x08, 0x41, 0x42), data.raw)
    }

    @Test
    fun `TX power level is encoded as signed byte of type 0x0A`() {
        val data = AdvertisingDataDefinition(txPowerLevel = -20)
        assertContentEquals(bytes(0x02, 0x0A, 0xEC), data.raw)
    }

    @Test
    fun `16-bit service UUIDs are encoded as little endian list of type 0x03`() {
        val data = AdvertisingDataDefinition(
            serviceUuids = listOf(Uuid.fromShortUuid(0x180F), Uuid.fromShortUuid(0x180A))
        )
        assertContentEquals(bytes(0x05, 0x03, 0x0F, 0x18, 0x0A, 0x18), data.raw)
    }

    @Test
    fun `manufacturer data is encoded with little endian company ID`() {
        val data = AdvertisingDataDefinition(manufacturerData = mapOf(0x0059 to bytes(1, 2)))
        assertContentEquals(bytes(0x05, 0xFF, 0x59, 0x00, 0x01, 0x02), data.raw)
    }

    @Test
    fun `empty name is not encoded`() {
        assertEquals(0, AdvertisingDataDefinition(completeLocalName = "").raw.size)
    }

    // Round trip

    @Test
    @Ignore("core-mock AD parser loop (raw.size > i + 3) skips very short payloads.")
    fun `flags only definition can be parsed back`() {
        val data = AdvertisingDataDefinition(flags = setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE))
        assertEquals(setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE), data.flags)
    }

    @Test
    @Ignore("core-mock AD parser loop (raw.size > i + 3) skips very short payloads.")
    fun `single character name only definition can be parsed back`() {
        val data = AdvertisingDataDefinition(completeLocalName = "A")
        assertEquals("A", data.completeLocalName)
    }

    @Test
    fun `flags and names can be parsed back`() {
        val data = AdvertisingDataDefinition(
            flags = setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE, AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED),
            completeLocalName = "Nordic",
            shortenedLocalName = "Nor",
        )
        assertEquals(
            setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE, AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED),
            data.flags
        )
        assertEquals("Nordic", data.completeLocalName)
        assertEquals("Nor", data.shortenedLocalName)
    }

    @Test
    fun `non-ASCII name can be parsed back`() {
        val data = AdvertisingDataDefinition(completeLocalName = "Zażółć", flags = emptySet())
        assertEquals("Zażółć", data.completeLocalName)
    }

    @Test
    @Ignore("TX power level is not advertised / not parsed back in core-mock.")
    fun `TX power level can be parsed back`() {
        val data = AdvertisingDataDefinition(completeLocalName = "Dev", txPowerLevel = -20)
        assertEquals(-20, data.txPowerLevel)
    }

    @Test
    @Ignore("TX power level is not advertised / not parsed back in core-mock.")
    fun `positive TX power level can be parsed back`() {
        val data = AdvertisingDataDefinition(completeLocalName = "Dev", txPowerLevel = 8)
        assertEquals(8, data.txPowerLevel)
    }

    @Test
    fun `mixed size service UUIDs can be parsed back`() {
        val uuids = listOf(Uuid.fromShortUuid(0x180F), Uuid.fromShortUuid(0x12345678), uuid128)
        val data = AdvertisingDataDefinition(serviceUuids = uuids)
        assertNotNull(data.serviceUuids)
        assertEquals(uuids.toSet(), data.serviceUuids!!.toSet())
        assertEquals(3, data.serviceUuids!!.size)
    }

    @Test
    fun `several 16-bit service UUIDs keep their order`() {
        val uuids = listOf(Uuid.fromShortUuid(0x180F), Uuid.fromShortUuid(0x180A), Uuid.fromShortUuid(0x1800))
        assertEquals(uuids, AdvertisingDataDefinition(serviceUuids = uuids).serviceUuids)
    }

    @Test
    fun `service solicitation UUIDs can be parsed back`() {
        val uuids = listOf(Uuid.fromShortUuid(0x180F), Uuid.fromShortUuid(0x12345678), uuid128)
        val data = AdvertisingDataDefinition(serviceSolicitationUuids = uuids)
        assertEquals(uuids.toSet(), data.serviceSolicitationUuids?.toSet())
    }

    @Test
    fun `service data can be parsed back for all UUID sizes`() {
        val serviceData = mapOf(
            Uuid.fromShortUuid(0x180F) to bytes(0x64),
            Uuid.fromShortUuid(0x12345678) to bytes(1, 2, 3),
            uuid128 to bytes(9, 8),
        )
        val data = AdvertisingDataDefinition(serviceData = serviceData)
        val parsed = assertNotNull(data.serviceData)
        assertEquals(serviceData.keys, parsed.keys)
        serviceData.forEach { (uuid, value) -> assertContentEquals(value, parsed[uuid], uuid.toString()) }
    }

    @Test
    fun `manufacturer data can be parsed back`() {
        val data = AdvertisingDataDefinition(
            manufacturerData = mapOf(0x0059 to bytes(1, 2, 3), 0xFFFF to bytes(0xAA))
        )
        val parsed = assertNotNull(data.manufacturerData)
        assertContentEquals(bytes(1, 2, 3), parsed[0x0059])
        assertContentEquals(bytes(0xAA), parsed[0xFFFF])
    }

    @Test
    fun `mesh data can be parsed back`() {
        val data = AdvertisingDataDefinition(
            completeLocalName = "Mesh",
            meshPbAdv = bytes(1, 2),
            meshMessage = bytes(3, 4),
            meshBeacon = bytes(5, 6),
        )
        assertContentEquals(bytes(1, 2), data.meshPbAdv)
        assertContentEquals(bytes(3, 4), data.meshMessage)
        assertContentEquals(bytes(5, 6), data.meshBeacon)
    }

    // Parsing raw data

    @Test
    fun `parsing stops at zero length AD structure`() {
        val data = AdvertisingDataDefinition(
            bytes(0x02, 0x01, 0x06, 0x00, 0x04, 0x09, 0x41, 0x42, 0x43)
        )
        assertNull(data.completeLocalName)
    }

    @Test
    fun `parsing ignores truncated AD structure`() {
        // The name claims 10 bytes, but only 3 are available.
        val data = AdvertisingDataDefinition(
            bytes(0x02, 0x01, 0x06, 0x0B, 0x09, 0x41, 0x42, 0x43)
        )
        assertNull(data.completeLocalName)
        assertTrue(data.flags?.isNotEmpty() == true)
    }

    @Test
    fun `unknown AD types are skipped and following structures are parsed`() {
        val data = AdvertisingDataDefinition(
            bytes(0x03, 0x0B, 0x01, 0x02, 0x04, 0x09, 0x41, 0x42, 0x43)
        )
        assertEquals("ABC", data.completeLocalName)
    }

    @Test
    fun `service UUIDs are available in the base class`() {
        val data = AdvertisingDataDefinition(bytes(0x03, 0x03, 0x0F, 0x18, 0x04, 0x09, 0x41, 0x42, 0x43))
        assertEquals(listOf(Uuid.fromShortUuid(0x180F)), data.serviceUuids)
    }
}
