package com.hapticHearing.haptics

/** Event vibration pattern. Timings alternate off, on, off, on… (ms); amplitudes are 0 for "off". */
class VibrationPattern(
    val id: String,
    val timings: LongArray,
    val amplitudes: IntArray,
) {
    init {
        require(timings.size == amplitudes.size)
    }

    val totalMs: Long get() = timings.sum()

    fun toWaveform() = Waveform(timings, amplitudes)

    companion object {
        /** Builds a pattern from off/on timings with one amplitude, repeated with [gapMs] between repeats. */
        fun of(id: String, offOn: LongArray, amplitude: Int, repeat: Int = 1, gapMs: Long = 300): VibrationPattern {
            val t = ArrayList<Long>()
            val a = ArrayList<Int>()
            repeat(repeat) { r ->
                offOn.forEachIndexed { i, ms ->
                    val off = i % 2 == 0
                    val duration = if (off && i == 0 && r > 0) ms + gapMs else ms
                    t += duration
                    a += if (off) 0 else amplitude
                }
            }
            return VibrationPattern(id, t.toLongArray(), a.toIntArray())
        }
    }
}

/** Preset patterns of spec §9.2. */
object VibrationPatterns {
    val NAME_CALL = VibrationPattern.of("NAME_CALL", longArrayOf(0, 400, 150, 400, 150, 800), 255, repeat = 3)
    val DANGER = VibrationPattern.of("DANGER", longArrayOf(0, 1000, 200, 1000, 200, 1000), 255)
    val HEARTBEAT = VibrationPattern.of("HEARTBEAT", longArrayOf(0, 150, 100, 150, 600), 200, repeat = 2, gapMs = 0)
    val DOORBELL = VibrationPattern.of("DOORBELL", longArrayOf(0, 250, 120, 600), 220)
    val RING = VibrationPattern.of("RING", longArrayOf(0, 100, 60, 100, 60, 100, 400), 200, repeat = 2, gapMs = 0)
    val PERSON_A = VibrationPattern.of("PERSON_A", longArrayOf(0, 600, 200, 600), 200)
    val PERSON_B = VibrationPattern.of("PERSON_B", longArrayOf(0, 150, 100, 150, 100, 150), 200)
    val PERSON_C = VibrationPattern.of("PERSON_C", longArrayOf(0, 600, 150, 150), 200)
    val PERSON_D = VibrationPattern.of("PERSON_D", longArrayOf(0, 150, 150, 600), 200)
    val GENERAL = VibrationPattern.of("GENERAL", longArrayOf(0, 200, 100, 200), 160)

    val all: List<VibrationPattern> = listOf(
        NAME_CALL, DANGER, HEARTBEAT, DOORBELL, RING, PERSON_A, PERSON_B, PERSON_C, PERSON_D, GENERAL,
    )

    fun byId(id: String): VibrationPattern? = all.firstOrNull { it.id == id }
}
