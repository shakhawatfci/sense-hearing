package com.hapticHearing.audio

/** Fixed capture format used by the whole pipeline. */
object AudioFormat {
    const val SAMPLE_RATE = 16_000

    /** 256 samples = 16 ms. */
    const val CHUNK_SAMPLES = 256

    /** About 10 s of audio kept for windowed consumers. */
    const val RING_SECONDS = 10
}
