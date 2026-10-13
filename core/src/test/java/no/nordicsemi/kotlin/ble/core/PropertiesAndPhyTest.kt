package no.nordicsemi.kotlin.ble.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertiesAndPhyTest {

    // CharacteristicProperty

    @Test
    fun `write properties map to write types`() {
        assertEquals(WriteType.WITH_RESPONSE, CharacteristicProperty.WRITE.writeType)
        assertEquals(WriteType.WITHOUT_RESPONSE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE.writeType)
        assertEquals(WriteType.SIGNED, CharacteristicProperty.SIGNED_WRITE.writeType)
    }

    @Test
    fun `non-write properties have no write type`() {
        listOf(
            CharacteristicProperty.BROADCAST,
            CharacteristicProperty.READ,
            CharacteristicProperty.NOTIFY,
            CharacteristicProperty.INDICATE,
            CharacteristicProperty.EXTENDED_PROPERTIES,
        ).forEach { assertNull(it.writeType, "$it") }
    }

    @Test
    fun `write type required property is the inverse of property write type`() {
        WriteType.entries.forEach { writeType ->
            assertEquals(writeType, writeType.requiredProperty.writeType)
        }
    }

    @Test
    fun `and creates a set of two properties`() {
        assertEquals(
            setOf(CharacteristicProperty.READ, CharacteristicProperty.NOTIFY),
            CharacteristicProperty.READ and CharacteristicProperty.NOTIFY
        )
    }

    @Test
    fun `and extends a set of properties`() {
        val properties = CharacteristicProperty.READ and CharacteristicProperty.NOTIFY and CharacteristicProperty.WRITE
        assertEquals(3, properties.size)
        assertTrue(CharacteristicProperty.WRITE in properties)
    }

    @Test
    fun `and with the same property twice yields a single element`() {
        assertEquals(1, (CharacteristicProperty.READ and CharacteristicProperty.READ).size)
    }

    @Test
    fun `default write type prefers write with response`() {
        val properties = CharacteristicProperty.WRITE_WITHOUT_RESPONSE and CharacteristicProperty.WRITE
        assertEquals(WriteType.WITH_RESPONSE, properties.defaultWriteType)
    }

    @Test
    fun `default write type falls back to write without response`() {
        assertEquals(
            WriteType.WITHOUT_RESPONSE,
            setOf(CharacteristicProperty.READ, CharacteristicProperty.WRITE_WITHOUT_RESPONSE).defaultWriteType
        )
    }

    @Test
    fun `default write type falls back to signed write`() {
        assertEquals(WriteType.SIGNED, setOf(CharacteristicProperty.SIGNED_WRITE).defaultWriteType)
    }

    @Test
    fun `default write type is null when characteristic is not writable`() {
        assertNull(setOf(CharacteristicProperty.READ, CharacteristicProperty.NOTIFY).defaultWriteType)
        assertNull(emptySet<CharacteristicProperty>().defaultWriteType)
    }

    // Permission

    @Test
    fun `permissions can be combined`() {
        assertEquals(setOf(Permission.READ, Permission.WRITE), Permission.READ and Permission.WRITE)
        assertEquals(
            setOf(Permission.READ, Permission.WRITE, Permission.WRITE_ENCRYPTED),
            Permission.READ and Permission.WRITE and Permission.WRITE_ENCRYPTED
        )
    }

    // Phy

    @Test
    fun `PHY rates are in bits per second`() {
        assertEquals(1_000_000, Phy.PHY_LE_1M.rate())
        assertEquals(2_000_000, Phy.PHY_LE_2M.rate())
    }

    @Test
    fun `Coded PHY is slower than 1M PHY`() {
        assertTrue(Phy.PHY_LE_CODED.rate() < Phy.PHY_LE_1M.rate())
    }

    @Test
    fun `PHY descriptions are human readable`() {
        assertEquals("LE 1M", Phy.PHY_LE_1M.toString())
        assertEquals("LE 2M", Phy.PHY_LE_2M.toString())
        assertEquals("LE Coded", Phy.PHY_LE_CODED.toString())
    }

    @Test
    fun `primary PHY converts to the matching PHY`() {
        assertEquals(Phy.PHY_LE_1M, PrimaryPhy.PHY_LE_1M.toPhy())
        assertEquals(Phy.PHY_LE_CODED, PrimaryPhy.PHY_LE_CODED.toPhy())
    }

    @Test
    fun `primary PHY has the same description as the matching PHY`() {
        PrimaryPhy.entries.forEach { assertEquals(it.toPhy().toString(), it.toString()) }
    }

    @Test
    fun `and builds lists of PHYs preserving order`() {
        assertEquals(listOf(Phy.PHY_LE_2M, Phy.PHY_LE_1M), Phy.PHY_LE_2M and Phy.PHY_LE_1M)
        assertEquals(
            listOf(Phy.PHY_LE_2M, Phy.PHY_LE_1M, Phy.PHY_LE_CODED),
            Phy.PHY_LE_2M and Phy.PHY_LE_1M and Phy.PHY_LE_CODED
        )
    }

    @Test
    fun `ANY primary PHY contains all primary PHYs`() {
        assertEquals(PrimaryPhy.entries, PrimaryPhy.PHY_LE_1M.ANY)
    }

    @Test
    fun `PhyInUse default uses 1M for both directions`() {
        assertEquals(PhyInUse(Phy.PHY_LE_1M, Phy.PHY_LE_1M), PhyInUse.PHY_LE_1M)
    }

    // MTU constants

    @Test
    fun `MTU constants match the Bluetooth specification`() {
        assertEquals(23, ATT_MTU_DEFAULT)
        assertEquals(517, ATT_MTU_MAX)
        assertEquals(27, LL_MTU_DEFAULT)
        assertEquals(251, LL_MTU_MAX)
    }
}
