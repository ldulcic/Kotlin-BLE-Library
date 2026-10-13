package no.nordicsemi.kotlin.ble.core.util

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransformTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ByteArray.chunked

    @Test
    fun `chunked of empty array returns empty list`() {
        assertTrue(byteArrayOf().chunked(3).isEmpty())
    }

    @Test
    fun `chunked with array not longer than size returns single chunk`() {
        val chunks = bytes(1, 2, 3).chunked(3)
        assertEquals(1, chunks.size)
        assertContentEquals(bytes(1, 2, 3), chunks[0])
    }

    @Test
    fun `chunked splits into equal chunks`() {
        val chunks = bytes(1, 2, 3, 4).chunked(2)
        assertEquals(2, chunks.size)
        assertContentEquals(bytes(1, 2), chunks[0])
        assertContentEquals(bytes(3, 4), chunks[1])
    }

    @Test
    fun `chunked puts remainder in the last chunk`() {
        val chunks = bytes(1, 2, 3, 4, 5).chunked(2)
        assertEquals(3, chunks.size)
        assertContentEquals(bytes(5), chunks[2])
    }

    @Test
    fun `chunked with size one returns every byte separately`() {
        assertEquals(3, bytes(1, 2, 3).chunked(1).size)
    }

    @Test
    fun `chunked with non positive size throws`() {
        assertFailsWith<IllegalArgumentException> { bytes(1).chunked(0) }
        assertFailsWith<IllegalArgumentException> { bytes(1).chunked(-1) }
    }

    @Test
    fun `chunked chunks concatenate to the original array`() {
        val original = ByteArray(100) { it.toByte() }
        val joined = original.chunked(7).fold(byteArrayOf()) { acc, chunk -> acc + chunk }
        assertContentEquals(original, joined)
    }

    // Flow.chunk

    @Test
    fun `chunk flow splits every emitted array`() = runTest {
        val result = flowOf(bytes(1, 2, 3), bytes(4))
            .chunk(2)
            .toList()
        assertEquals(3, result.size)
        assertContentEquals(bytes(1, 2), result[0])
        assertContentEquals(bytes(3), result[1])
        assertContentEquals(bytes(4), result[2])
    }

    @Test
    fun `chunk flow with custom operation emits operation result`() = runTest {
        val result = flowOf(bytes(1, 2, 3))
            .chunk(2) { data, size -> listOf(data, bytes(size)) }
            .toList()
        assertEquals(2, result.size)
        assertContentEquals(bytes(1, 2, 3), result[0])
        assertContentEquals(bytes(2), result[1])
    }

    // Flow.merge

    @Test
    fun `merge accumulates until completed`() = runTest {
        val result = flowOf(bytes(1), bytes(2), bytes(3))
            .merge { accumulator, received ->
                val merged = accumulator + received
                if (merged.size == 2) MergeResult.Completed(merged) else MergeResult.Accumulate(merged)
            }
            .toList()
        // The 3rd element stays in the accumulator, as it is never completed.
        assertEquals(1, result.size)
        assertContentEquals(bytes(1, 2), result[0])
    }

    @Test
    fun `merge resets accumulator after completion`() = runTest {
        val result = flowOf(bytes(1), bytes(2), bytes(3), bytes(4))
            .merge { accumulator, received ->
                val merged = accumulator + received
                if (merged.size == 2) MergeResult.Completed(merged) else MergeResult.Accumulate(merged)
            }
            .toList()
        assertEquals(2, result.size)
        assertContentEquals(bytes(3, 4), result[1])
    }

    @Test
    fun `merge of empty flow emits nothing`() = runTest {
        val result = flowOf<ByteArray>()
            .merge { _, received -> MergeResult.Completed(received) }
            .toList()
        assertTrue(result.isEmpty())
    }

    // Flow.mergeIndexed

    @Test
    fun `mergeIndexed passes increasing index`() = runTest {
        val indices = mutableListOf<Int>()
        flowOf(bytes(1), bytes(2), bytes(3))
            .mergeIndexed { accumulator, received, index ->
                indices += index
                MergeResult.Accumulate(accumulator + received)
            }
            .toList()
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun `mergeIndexed resets index after completion`() = runTest {
        val indices = mutableListOf<Int>()
        val result = flowOf(bytes(1), bytes(2), bytes(3), bytes(4))
            .mergeIndexed { accumulator, received, index ->
                indices += index
                val merged = accumulator + received
                if (index == 1) MergeResult.Completed(merged) else MergeResult.Accumulate(merged)
            }
            .toList()
        assertEquals(listOf(0, 1, 0, 1), indices)
        assertEquals(2, result.size)
        assertContentEquals(bytes(3, 4), result[1])
    }
}
