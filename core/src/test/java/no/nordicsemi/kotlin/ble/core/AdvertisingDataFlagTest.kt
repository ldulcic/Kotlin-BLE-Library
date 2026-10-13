@file:Suppress("DEPRECATION")

package no.nordicsemi.kotlin.ble.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdvertisingDataFlagTest {

    @Test
    fun `flag masks match the Bluetooth Core Specification Supplement`() {
        assertEquals(0x01, AdvertisingDataFlag.LE_LIMITED_DISCOVERABLE_MODE.mask.toInt())
        assertEquals(0x02, AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE.mask.toInt())
        assertEquals(0x04, AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED.mask.toInt())
        assertEquals(0x08, AdvertisingDataFlag.SIMULTANEOUS_LE_BR_EDR_TO_SAME_DEVICE_CAPABLE_CONTROLLER.mask.toInt())
        assertEquals(0x10, AdvertisingDataFlag.SIMULTANEOUS_LE_BR_EDR_TO_SAME_DEVICE_CAPABLE_HOST.mask.toInt())
    }

    @Test
    fun `empty flag set has value zero`() {
        assertEquals(0, emptySet<AdvertisingDataFlag>().value.toInt())
    }

    @Test
    fun `flag set value is the bitwise OR of masks`() {
        val flags = setOf(
            AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE,
            AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED
        )
        assertEquals(0x06, flags.value.toInt())
    }

    @Test
    fun `all flags together have value 0x1F`() {
        assertEquals(0x1F, AdvertisingDataFlag.entries.toSet().value.toInt())
    }

    @Test
    fun `Int zero has no flags`() {
        assertTrue(0.asFlags().isEmpty())
    }

    @Test
    fun `Int is converted to the set of flags`() {
        assertEquals(
            setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE, AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED),
            0x06.asFlags()
        )
    }

    @Test
    fun `reserved bits are ignored`() {
        assertEquals(emptySet(), 0xE0.asFlags())
        assertEquals(setOf(AdvertisingDataFlag.LE_LIMITED_DISCOVERABLE_MODE), 0xE1.asFlags())
    }

    @Test
    fun `Byte with highest bit set is converted correctly`() {
        assertEquals(
            setOf(AdvertisingDataFlag.LE_GENERAL_DISCOVERABLE_MODE, AdvertisingDataFlag.BR_EDR_NOT_SUPPORTED),
            0x86.toByte().asFlags()
        )
    }

    @Test
    fun `flags survive a round trip through value`() {
        AdvertisingDataFlag.entries.forEach { flag ->
            assertEquals(setOf(flag), setOf(flag).value.asFlags())
        }
    }

    @Test
    fun `createOrNull finds known AD types and returns null for unknown`() {
        assertEquals(AdvertisingDataType.FLAGS, AdvertisingDataType.createOrNull(0x01))
        assertEquals(AdvertisingDataType.COMPLETE_LOCAL_NAME, AdvertisingDataType.createOrNull(0x09))
        assertEquals(AdvertisingDataType.MANUFACTURER_SPECIFIC_DATA, AdvertisingDataType.createOrNull(0xFF))
        assertNull(AdvertisingDataType.createOrNull(0x00))
        assertNull(AdvertisingDataType.createOrNull(0x0B))
        assertNull(AdvertisingDataType.createOrNull(0x100))
    }

    @Test
    fun `AD types have unique values`() {
        val values = AdvertisingDataType.entries.map { it.type }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun `AD type values match Assigned Numbers`() {
        assertEquals(0x03, AdvertisingDataType.COMPLETE_LIST_OF_16_BIT_SERVICE_UUIDS.type)
        assertEquals(0x07, AdvertisingDataType.COMPLETE_LIST_OF_128_BIT_SERVICE_UUIDS.type)
        assertEquals(0x0A, AdvertisingDataType.TX_POWER_LEVEL.type)
        assertEquals(0x16, AdvertisingDataType.SERVICE_DATA_16_BIT.type)
        assertEquals(0x19, AdvertisingDataType.APPEARANCE.type)
        assertEquals(0x24, AdvertisingDataType.URI.type)
        assertEquals(0x29, AdvertisingDataType.PB_ADV.type)
        assertEquals(0x2A, AdvertisingDataType.MESH_MESSAGE.type)
        assertEquals(0x2B, AdvertisingDataType.MESH_BEACON.type)
    }
}
