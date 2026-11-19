package com.hapticHearing.dsp

/** Settings the DSP needs. Levels are in dBFS (dB SPL minus the calibration offset). */
data class DspConfig(
    val minHz: Float = 100f,
    val maxHz: Float = 8000f,
    /** userMinDb − calibrationOffset. */
    val thresholdDbfs: Float = -50f,
    val noiseMarginDb: Float = 8f,
)

/** One analysed frame (every 16 ms). */
class DspFrame(
    val timeNanos: Long,
    val bandDbfs: FloatArray,
    val floorDbfs: FloatArray,
    val active: BooleanArray,
    val overallDbfs: Float,
    val bandEdgesHz: FloatArray,
    val thresholdDbfs: Float,
    val noiseMarginDb: Float,
) {
    val anyActive: Boolean get() = active.any { it }
}

/**
 * Sliding 512-sample window, hop 256 (16 ms at 16 kHz). For every hop: band levels, noise floors and
 * the "active" decision of spec §5.1–5.2.
 */
class DspProcessor(
    val sampleRate: Int = 16_000,
    val fftSize: Int = 512,
    val hop: Int = 256,
    val numBands: Int = 3,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    val analyzer = BandAnalyzer(sampleRate, fftSize, numBands)
    private val trackers = Array(numBands) { NoiseFloorTracker() }
    private val frame = FloatArray(fftSize)
    private val staging = FloatArray(hop)
    private var stagingCount = 0
    private var hopsSeen = 0
    private val frameDtSec = hop.toFloat() / sampleRate

    @Volatile var config = DspConfig()

    /**
     * Extra margin (dB) per band while the phone's own motor is running, so the motor buzz picked up
     * by the microphone cannot keep the vibration going by itself.
     */
    @Volatile var selfVibrationGuard: Boolean = false
    var selfVibrationGuardDb = floatArrayOf(6f, 3f, 0f)

    private var appliedMinHz = Float.NaN
    private var appliedMaxHz = Float.NaN

    fun reset() {
        frame.fill(0f)
        stagingCount = 0
        hopsSeen = 0
        trackers.forEach { it.reset() }
    }

    /** Feeds any number of samples; [onFrame] is called once per completed hop. */
    fun process(samples: FloatArray, length: Int = samples.size, onFrame: (DspFrame) -> Unit) {
        var i = 0
        while (i < length) {
            val take = minOf(hop - stagingCount, length - i)
            System.arraycopy(samples, i, staging, stagingCount, take)
            stagingCount += take
            i += take
            if (stagingCount == hop) {
                System.arraycopy(frame, hop, frame, 0, fftSize - hop)
                System.arraycopy(staging, 0, frame, fftSize - hop, hop)
                stagingCount = 0
                hopsSeen++
                if (hopsSeen * hop >= fftSize) onFrame(analyzeFrame())
            }
        }
    }

    private fun analyzeFrame(): DspFrame {
        val cfg = config
        if (cfg.minHz != appliedMinHz || cfg.maxHz != appliedMaxHz) {
            analyzer.setRange(cfg.minHz, cfg.maxHz)
            appliedMinHz = cfg.minHz
            appliedMaxHz = cfg.maxHz
            trackers.forEach { it.reset() }
        }
        val levels = FloatArray(numBands)
        val overall = analyzer.analyze(frame, levels)
        val floors = FloatArray(numBands)
        val active = BooleanArray(numBands)
        val guard = selfVibrationGuard
        for (b in 0 until numBands) {
            val f = trackers[b].update(levels[b], frameDtSec)
            floors[b] = f
            val margin = cfg.noiseMarginDb + if (guard) selfVibrationGuardDb.getOrElse(b) { 0f } else 0f
            active[b] = levels[b] >= cfg.thresholdDbfs && levels[b] >= f + margin
        }
        return DspFrame(
            timeNanos = nanoClock(),
            bandDbfs = levels,
            floorDbfs = floors,
            active = active,
            overallDbfs = overall,
            bandEdgesHz = analyzer.bandEdgesHz,
            thresholdDbfs = cfg.thresholdDbfs,
            noiseMarginDb = cfg.noiseMarginDb,
        )
    }
}
