package com.hapticHearing.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RingBufferTest {
    @Test
    fun `readLast zero-fills missing history`() {
        val rb = RingBuffer(8)
        rb.write(floatArrayOf(1f, 2f))
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 2f), rb.readLast(4), 0f)
    }

    @Test
    fun `wraps around`() {
        val rb = RingBuffer(5)
        rb.write(floatArrayOf(1f, 2f, 3f, 4f))
        rb.write(floatArrayOf(5f, 6f, 7f))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f, 7f), rb.readLast(5), 0f)
        assertEquals(7L, rb.totalWritten)
    }

    @Test
    fun `read by absolute position skips overwritten samples`() {
        val rb = RingBuffer(4)
        rb.write(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f))
        val dest = FloatArray(4)
        val n = rb.read(0, dest)
        assertEquals(4, n)
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f), dest, 0f)
        assertEquals(0, rb.read(6, dest))
    }
}
