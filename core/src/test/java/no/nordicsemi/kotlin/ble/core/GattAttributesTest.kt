package no.nordicsemi.kotlin.ble.core

import no.nordicsemi.kotlin.ble.core.util.fromShortUuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Tests of default methods and well known UUIDs of GATT attributes. */
class GattAttributesTest {

    private class FakeService(
        override val uuid: Uuid,
    ) : PrimaryService<FakeCharacteristic> {
        override val owner: Peer<*>? = null
        override val instanceId: Int = 0
        override val characteristics: List<FakeCharacteristic> = emptyList()
        override val includedServices: List<IncludedService<FakeCharacteristic>> = emptyList()
    }

    private class FakeDescriptor(
        override val uuid: Uuid,
        override val characteristic: Characteristic<*>,
    ) : Descriptor {
        override val owner: Peer<*>? = null
        override val instanceId: Int = 0
    }

    private class FakeCharacteristic(
        override val uuid: Uuid,
        override val properties: Set<CharacteristicProperty>,
        serviceUuid: Uuid = Uuid.fromShortUuid(0x180F),
        descriptorUuids: List<Uuid> = emptyList(),
    ) : Characteristic<FakeDescriptor> {
        override val owner: Peer<*>? = null
        override val instanceId: Int = 0
        override val service: AnyService<out Characteristic<FakeDescriptor>> =
            FakeService(serviceUuid)
        override val descriptors: List<FakeDescriptor> = descriptorUuids.map { FakeDescriptor(it, this) }
    }

    private fun characteristic(
        vararg properties: CharacteristicProperty,
        descriptors: List<Uuid> = emptyList(),
        service: Uuid = Uuid.fromShortUuid(0x180F),
        uuid: Uuid = Uuid.fromShortUuid(0x2A19),
    ) = FakeCharacteristic(uuid, properties.toSet(), service, descriptors)

    // Well known UUIDs

    @Test
    fun `well known service UUIDs`() {
        assertEquals(Uuid.parse("00001800-0000-1000-8000-00805f9b34fb"), Service.GENERIC_ACCESS_UUID)
        assertEquals(Uuid.parse("00001801-0000-1000-8000-00805f9b34fb"), Service.GENERIC_ATTRIBUTE_UUID)
    }

    @Test
    fun `well known characteristic UUIDs`() {
        assertEquals(Uuid.fromShortUuid(0x2A00), Characteristic.DEVICE_NAME)
        assertEquals(Uuid.fromShortUuid(0x2A01), Characteristic.APPEARANCE)
        assertEquals(Uuid.fromShortUuid(0x2A04), Characteristic.PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS)
        assertEquals(Uuid.fromShortUuid(0x2A05), Characteristic.SERVICE_CHANGED)
    }

    @Test
    fun `well known descriptor UUIDs`() {
        assertEquals(Uuid.fromShortUuid(0x2900), Descriptor.CHAR_EXT_PROP_UUID)
        assertEquals(Uuid.fromShortUuid(0x2901), Descriptor.CHAR_USER_DESC_UUID)
        assertEquals(Uuid.fromShortUuid(0x2902), Descriptor.CLIENT_CHAR_CONF_UUID)
        assertEquals(Uuid.fromShortUuid(0x2903), Descriptor.SERVER_CHAR_CONF_UUID)
        assertEquals(Uuid.fromShortUuid(0x2904), Descriptor.CHAR_PRESENTATION_FORMAT_UUID)
    }

    // Characteristic

    @Test
    fun `characteristic with READ property is readable`() {
        assertTrue(characteristic(CharacteristicProperty.READ).isReadable())
        assertFalse(characteristic(CharacteristicProperty.WRITE).isReadable())
    }

    @Test
    fun `characteristic is writable with any write property`() {
        assertTrue(characteristic(CharacteristicProperty.WRITE).isWritable())
        assertTrue(characteristic(CharacteristicProperty.WRITE_WITHOUT_RESPONSE).isWritable())
        assertTrue(characteristic(CharacteristicProperty.SIGNED_WRITE).isWritable())
    }

    @Test
    fun `characteristic without write property is not writable`() {
        assertFalse(characteristic(CharacteristicProperty.READ, CharacteristicProperty.NOTIFY).isWritable())
    }

    @Test
    fun `characteristic with NOTIFY and CCCD is subscribable`() {
        assertTrue(
            characteristic(CharacteristicProperty.NOTIFY, descriptors = listOf(Descriptor.CLIENT_CHAR_CONF_UUID))
                .isSubscribable()
        )
    }

    @Test
    fun `characteristic with INDICATE and CCCD is subscribable`() {
        assertTrue(
            characteristic(CharacteristicProperty.INDICATE, descriptors = listOf(Descriptor.CLIENT_CHAR_CONF_UUID))
                .isSubscribable()
        )
    }

    @Test
    fun `characteristic with NOTIFY but without CCCD is not subscribable`() {
        assertFalse(characteristic(CharacteristicProperty.NOTIFY).isSubscribable())
    }

    @Test
    fun `characteristic with CCCD but without NOTIFY or INDICATE is not subscribable`() {
        assertFalse(
            characteristic(CharacteristicProperty.READ, descriptors = listOf(Descriptor.CLIENT_CHAR_CONF_UUID))
                .isSubscribable()
        )
    }

    @Test
    fun `HID report characteristics are restricted`() {
        val hid = Uuid.fromShortUuid(0x1812)
        listOf(0x2A4A, 0x2A4B, 0x2A4C, 0x2A4D).forEach {
            assertTrue(
                characteristic(CharacteristicProperty.READ, service = hid, uuid = Uuid.fromShortUuid(it)).isRestricted(),
                "0x${it.toString(16)}"
            )
        }
    }

    @Test
    fun `other characteristics of HID service are not restricted`() {
        val hid = Uuid.fromShortUuid(0x1812)
        assertFalse(characteristic(CharacteristicProperty.READ, service = hid, uuid = Uuid.fromShortUuid(0x2A4E)).isRestricted())
    }

    @Test
    fun `HID characteristic UUIDs in another service are not restricted`() {
        assertFalse(characteristic(CharacteristicProperty.READ, uuid = Uuid.fromShortUuid(0x2A4D)).isRestricted())
    }

    @Test
    fun `characteristic of a restricted service is restricted`() {
        assertTrue(
            characteristic(CharacteristicProperty.READ, service = Uuid.fromShortUuid(0xFFFD)).isRestricted()
        )
    }

    // Service

    @Test
    fun `LE Audio, FIDO and Android specific services are restricted`() {
        listOf(
            Uuid.fromShortUuid(0xFFFD),
            Uuid.fromShortUuid(0x1843),
            Uuid.fromShortUuid(0x1844),
            Uuid.fromShortUuid(0x1850),
            Uuid.parse("AB5E0001-5A21-4F05-BC7D-AF01F617B664"),
        ).forEach { assertTrue(FakeService(it).isRestricted(), it.toString()) }
    }

    @Test
    fun `regular services are not restricted`() {
        listOf(
            Service.GENERIC_ACCESS_UUID,
            Uuid.fromShortUuid(0x180F),
            Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
        ).forEach { assertFalse(FakeService(it).isRestricted(), it.toString()) }
    }

    // Descriptor

    @Test
    fun `descriptor is client characteristic configuration only for 0x2902`() {
        val c = characteristic(CharacteristicProperty.NOTIFY)
        assertTrue(FakeDescriptor(Descriptor.CLIENT_CHAR_CONF_UUID, c).isClientCharacteristicConfiguration)
        assertFalse(FakeDescriptor(Descriptor.CHAR_USER_DESC_UUID, c).isClientCharacteristicConfiguration)
    }

    @Test
    fun `extended properties presentation format and aggregate format descriptors are read-only`() {
        val c = characteristic(CharacteristicProperty.READ)
        assertFalse(FakeDescriptor(Descriptor.CHAR_EXT_PROP_UUID, c).isWritable())
        assertFalse(FakeDescriptor(Descriptor.CHAR_PRESENTATION_FORMAT_UUID, c).isWritable())
        assertFalse(FakeDescriptor(Descriptor.CHAR_AGGREGATE_FORMAT_UUID, c).isWritable())
    }

    @Test
    fun `CCCD and user description descriptors are writable`() {
        val c = characteristic(CharacteristicProperty.NOTIFY)
        assertTrue(FakeDescriptor(Descriptor.CLIENT_CHAR_CONF_UUID, c).isWritable())
        assertTrue(FakeDescriptor(Descriptor.CHAR_USER_DESC_UUID, c).isWritable())
    }

    @Test
    fun `descriptor of restricted characteristic is restricted`() {
        val restricted = characteristic(CharacteristicProperty.READ, service = Uuid.fromShortUuid(0xFFFD))
        val regular = characteristic(CharacteristicProperty.READ)
        assertTrue(FakeDescriptor(Descriptor.CHAR_USER_DESC_UUID, restricted).isRestricted())
        assertFalse(FakeDescriptor(Descriptor.CHAR_USER_DESC_UUID, regular).isRestricted())
    }

    @Test
    fun `descriptors are readable by default`() {
        assertTrue(FakeDescriptor(Uuid.fromShortUuid(0x2910), characteristic(CharacteristicProperty.READ)).isReadable())
    }
}
