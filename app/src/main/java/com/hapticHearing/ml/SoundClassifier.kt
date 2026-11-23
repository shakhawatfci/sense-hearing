package com.hapticHearing.ml

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** General sound classifier (YAMNet). An interface so the pipeline logic can be tested without MediaPipe. */
interface SoundModel : AutoCloseable {
    /** Number of 16 kHz samples per classification window. */
    val windowSamples: Int
    fun classify(samples: FloatArray): List<LabelScore>
}

/** MediaPipe Audio Classifier + YAMNet in AUDIO_CLIPS mode on 0.975 s windows (spec §6). */
class MediaPipeSoundModel(context: Context) : SoundModel {
    override val windowSamples = WINDOW_SAMPLES

    private val classifier: AudioClassifier = AudioClassifier.createFromOptions(
        context,
        AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(5)
            .setScoreThreshold(0.2f)
            .build(),
    )

    private val format = AudioData.AudioDataFormat.builder()
        .setNumOfChannels(1)
        .setSampleRate(16_000f)
        .build()

    override fun classify(samples: FloatArray): List<LabelScore> {
        val data = AudioData.create(format, samples.size)
        data.load(samples)
        val result = classifier.classify(data)
        // A 0.975 s clip yields one result; take the last one if the model splits it.
        val categories = result.classificationResults().lastOrNull()
            ?.classifications()?.firstOrNull()
            ?.categories()
            ?: return emptyList()
        return categories.map { LabelScore(it.categoryName(), it.score()) }
    }

    override fun close() = classifier.close()

    companion object {
        const val MODEL_ASSET = "models/yamnet.tflite"
        const val WINDOW_SAMPLES = 15_600
    }
}

@Singleton
class SoundModelFactory @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(): SoundModel = MediaPipeSoundModel(context)
    fun table(): SoundCategoryTable = SoundCategoryTable.load(context)
}
