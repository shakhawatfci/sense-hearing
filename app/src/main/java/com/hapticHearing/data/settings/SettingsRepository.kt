package com.hapticHearing.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** All user settings (spec §11). dB values are approximate dB SPL. */
data class AppSettings(
    val minDb: Float = 50f,
    val minHz: Float = 100f,
    val maxHz: Float = 8000f,
    val noiseMarginDb: Float = 8f,
    val vibrationStrength: Float = 1f,
    val continuousEnabled: Boolean = true,
    val speakerThreshold: Float = 0.50f,
    /** Negative = not calibrated yet (set automatically during name setup, M6). */
    val nameThreshold: Float = -1f,
    val calibrationOffset: Float = DEFAULT_CALIBRATION_OFFSET,
    val quietMode: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val batterySaver: Boolean = false,
    val language: String = "system",
    val onboardingDone: Boolean = false,
    /** True while the user wants listening on; used to offer "resume" after a reboot. */
    val wasListening: Boolean = false,
) {
    val thresholdDbfs: Float get() = minDb - calibrationOffset

    companion object {
        const val DEFAULT_CALIBRATION_OFFSET = 100f
        const val MIN_DB_RANGE_START = 30f
        const val MIN_DB_RANGE_END = 90f
        const val HZ_RANGE_START = 50f
        const val HZ_RANGE_END = 8000f
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.dataStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.minDb] = s.minDb
            prefs[K.minHz] = s.minHz
            prefs[K.maxHz] = s.maxHz
            prefs[K.noiseMarginDb] = s.noiseMarginDb
            prefs[K.vibrationStrength] = s.vibrationStrength
            prefs[K.continuousEnabled] = s.continuousEnabled
            prefs[K.speakerThreshold] = s.speakerThreshold
            prefs[K.nameThreshold] = s.nameThreshold
            prefs[K.calibrationOffset] = s.calibrationOffset
            prefs[K.quietMode] = s.quietMode
            prefs[K.quietHoursEnabled] = s.quietHoursEnabled
            prefs[K.quietStart] = s.quietStartMinute
            prefs[K.quietEnd] = s.quietEndMinute
            prefs[K.batterySaver] = s.batterySaver
            prefs[K.language] = s.language
            prefs[K.onboardingDone] = s.onboardingDone
            prefs[K.wasListening] = s.wasListening
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            minDb = this[K.minDb] ?: d.minDb,
            minHz = this[K.minHz] ?: d.minHz,
            maxHz = this[K.maxHz] ?: d.maxHz,
            noiseMarginDb = this[K.noiseMarginDb] ?: d.noiseMarginDb,
            vibrationStrength = this[K.vibrationStrength] ?: d.vibrationStrength,
            continuousEnabled = this[K.continuousEnabled] ?: d.continuousEnabled,
            speakerThreshold = this[K.speakerThreshold] ?: d.speakerThreshold,
            nameThreshold = this[K.nameThreshold] ?: d.nameThreshold,
            calibrationOffset = this[K.calibrationOffset] ?: d.calibrationOffset,
            quietMode = this[K.quietMode] ?: d.quietMode,
            quietHoursEnabled = this[K.quietHoursEnabled] ?: d.quietHoursEnabled,
            quietStartMinute = this[K.quietStart] ?: d.quietStartMinute,
            quietEndMinute = this[K.quietEnd] ?: d.quietEndMinute,
            batterySaver = this[K.batterySaver] ?: d.batterySaver,
            language = this[K.language] ?: d.language,
            onboardingDone = this[K.onboardingDone] ?: d.onboardingDone,
            wasListening = this[K.wasListening] ?: d.wasListening,
        )
    }

    private object K {
        val minDb = floatPreferencesKey("minDb")
        val minHz = floatPreferencesKey("minHz")
        val maxHz = floatPreferencesKey("maxHz")
        val noiseMarginDb = floatPreferencesKey("noiseMarginDb")
        val vibrationStrength = floatPreferencesKey("vibrationStrength")
        val continuousEnabled = booleanPreferencesKey("continuousEnabled")
        val speakerThreshold = floatPreferencesKey("speakerThreshold")
        val nameThreshold = floatPreferencesKey("nameThreshold")
        val calibrationOffset = floatPreferencesKey("calibrationOffset")
        val quietMode = booleanPreferencesKey("quietMode")
        val quietHoursEnabled = booleanPreferencesKey("quietHoursEnabled")
        val quietStart = intPreferencesKey("quietStartMinute")
        val quietEnd = intPreferencesKey("quietEndMinute")
        val batterySaver = booleanPreferencesKey("batterySaver")
        val language = stringPreferencesKey("language")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val wasListening = booleanPreferencesKey("wasListening")
    }
}
