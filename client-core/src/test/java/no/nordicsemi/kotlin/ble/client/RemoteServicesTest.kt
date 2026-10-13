package no.nordicsemi.kotlin.ble.client

import no.nordicsemi.kotlin.ble.core.util.fromShortUuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.uuid.Uuid

class RemoteServicesTest {

    private class FakeRemoteService(override val uuid: Uuid) : RemoteService() {
        override val instanceId: Int = 0
        override val characteristics: List<RemoteCharacteristic> = emptyList()
        override val includedServices: List<RemoteIncludedService> = emptyList()
    }

    private val battery = FakeRemoteService(Uuid.fromShortUuid(0x180F))
    private val deviceInfo = FakeRemoteService(Uuid.fromShortUuid(0x180A))
    private val heartRate = FakeRemoteService(Uuid.fromShortUuid(0x180D))

    @Test
    fun `filtering keeps only requested services`() {
        val discovered = RemoteServices.Discovered(listOf(battery, deviceInfo, heartRate))
        val filtered = discovered.filteredBy(listOf(Uuid.fromShortUuid(0x180F), Uuid.fromShortUuid(0x180D)))
        assertIs<RemoteServices.Discovered>(filtered)
        assertEquals(listOf(battery, heartRate), filtered.services)
    }

    @Test
    fun `filtering keeps order of discovered services`() {
        val discovered = RemoteServices.Discovered(listOf(battery, deviceInfo, heartRate))
        val filtered = discovered.filteredBy(listOf(Uuid.fromShortUuid(0x180D), Uuid.fromShortUuid(0x180F)))
        assertIs<RemoteServices.Discovered>(filtered)
        assertEquals(listOf(battery, heartRate), filtered.services)
    }

    @Test
    fun `filtering by empty list returns no services`() {
        val filtered = RemoteServices.Discovered(listOf(battery)).filteredBy(emptyList())
        assertIs<RemoteServices.Discovered>(filtered)
        assertEquals(emptyList(), filtered.services)
    }

    @Test
    fun `filtering by missing UUID returns no services`() {
        val filtered = RemoteServices.Discovered(listOf(battery)).filteredBy(listOf(Uuid.fromShortUuid(0x1800)))
        assertIs<RemoteServices.Discovered>(filtered)
        assertEquals(emptyList(), filtered.services)
    }

    @Test
    fun `filtering does not change states other than Discovered`() {
        val uuids = listOf(Uuid.fromShortUuid(0x180F))
        assertSame(RemoteServices.Unknown, RemoteServices.Unknown.filteredBy(uuids))
        assertSame(RemoteServices.Discovering, RemoteServices.Discovering.filteredBy(uuids))
        val failed = RemoteServices.Failed(RemoteServices.Failed.Reason.EmptyResult)
        assertSame(failed, failed.filteredBy(uuids))
    }

    @Test
    fun `failed reasons with different status are not equal`() {
        assertEquals(
            RemoteServices.Failed(RemoteServices.Failed.Reason.Unknown(1)),
            RemoteServices.Failed(RemoteServices.Failed.Reason.Unknown(1))
        )
        assertEquals(
            false,
            RemoteServices.Failed.Reason.Unknown(1) == RemoteServices.Failed.Reason.Unknown(2)
        )
    }
}
