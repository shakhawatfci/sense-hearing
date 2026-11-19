package com.hapticHearing.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * Single-writer / multi-reader float ring buffer.
 *
 * The writer (audio thread) never blocks. Readers copy the most recent samples; because the
 * capacity (~10 s) is far larger than any window a reader asks for (at most 4 s), the region being
 * copied is not overwritten during the copy in practice. [totalWritten] lets readers track their
 * own position.
 */
class RingBuffer(val capacity: Int) {
    private val data = FloatArray(capacity)
    private val written = AtomicLong(0)

    /** Total samples written since creation (monotonic). */
    val totalWritten: Long get() = written.get()

    fun write(src: FloatArray, length: Int = src.size) {
        var pos = (written.get() % capacity).toInt()
        var remaining = length
        var offset = 0
        while (remaining > 0) {
            val n = minOf(remaining, capacity - pos)
            System.arraycopy(src, offset, data, pos, n)
            offset += n
            remaining -= n
            pos = (pos + n) % capacity
        }
        written.addAndGet(length.toLong())
    }

    /** Copies the last [n] samples (oldest first). Missing history is zero-filled at the start. */
    fun readLast(n: Int): FloatArray {
        require(n in 0..capacity) { "n must be within 0..capacity" }
        val out = FloatArray(n)
        val end = written.get()
        val available = minOf(end, n.toLong()).toInt()
        copyRange(end - available, available, out, n - available)
        return out
    }

    /** Copies samples [fromSample, fromSample + n) that are still buffered; returns the count copied. */
    fun read(fromSample: Long, dest: FloatArray, n: Int = dest.size): Int {
        val end = written.get()
        val oldest = maxOf(0L, end - capacity)
        val from = maxOf(fromSample, oldest)
        val count = minOf(n.toLong(), end - from).toInt()
        if (count <= 0) return 0
        copyRange(from, count, dest, 0)
        return count
    }

    private fun copyRange(start: Long, count: Int, dest: FloatArray, destOffset: Int) {
        var pos = (start % capacity).toInt()
        var remaining = count
        var off = destOffset
        while (remaining > 0) {
            val n = minOf(remaining, capacity - pos)
            System.arraycopy(data, pos, dest, off, n)
            off += n
            remaining -= n
            pos = (pos + n) % capacity
        }
    }
}
