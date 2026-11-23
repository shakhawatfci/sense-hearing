package com.hapticHearing.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.hapticHearing.R
import com.hapticHearing.data.settings.AppSettings
import com.hapticHearing.data.settings.SettingsRepository
import com.hapticHearing.haptics.VibrationEngine
import com.hapticHearing.haptics.VibrationPatterns
import com.hapticHearing.service.HearingPipeline
import com.hapticHearing.service.ListeningStatus
import com.hapticHearing.service.LiveState
import com.hapticHearing.ui.components.SectionCard
import com.hapticHearing.ui.components.SettingSlider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface CalibrationState {
    data object Idle : CalibrationState
    data object Measuring : CalibrationState
    data object NeedsListening : CalibrationState
    data class Done(val offset: Float) : CalibrationState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val pipeline: HearingPipeline,
    private val vibration: VibrationEngine,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val live: StateFlow<LiveState> = pipeline.live

    private val _calibration = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val calibration: StateFlow<CalibrationState> = _calibration.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(transform) }

    fun testVibration() = vibration.playPattern(VibrationPatterns.GENERAL, settings.value.vibrationStrength.coerceAtLeast(0.3f))

    /** Measures the level for 3 s while the user talks at ~60 dB, then sets the offset. */
    fun calibrate() = viewModelScope.launch {
        if (live.value.status != ListeningStatus.LISTENING) {
            _calibration.value = CalibrationState.NeedsListening
            return@launch
        }
        _calibration.value = CalibrationState.Measuring
        val offsetNow = settings.value.calibrationOffset
        val samples = ArrayList<Float>()
        repeat(60) {
            val s = live.value
            if (s.hasSignal) samples += s.overallDb - offsetNow
            delay(50)
        }
        if (samples.isEmpty()) {
            _calibration.value = CalibrationState.NeedsListening
            return@launch
        }
        // Use the louder half (speech has pauses).
        val sorted = samples.sorted()
        val loud = sorted.subList(sorted.size / 2, sorted.size).average().toFloat()
        val offset = (TARGET_DB - loud).coerceIn(40f, 160f)
        repo.update { it.copy(calibrationOffset = offset) }
        _calibration.value = CalibrationState.Done(offset)
    }

    fun resetCalibration() = viewModelScope.launch {
        repo.update { it.copy(calibrationOffset = AppSettings.DEFAULT_CALIBRATION_OFFSET) }
        _calibration.value = CalibrationState.Idle
    }

    private companion object {
        const val TARGET_DB = 60f
    }
}

private data class HzPreset(val label: Int, val min: Float, val max: Float)

private val hzPresets = listOf(
    HzPreset(R.string.preset_all, 50f, 8000f),
    HzPreset(R.string.preset_voices, 300f, 3400f),
    HzPreset(R.string.preset_low, 50f, 500f),
    HzPreset(R.string.preset_high, 2000f, 8000f),
)

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenHelp: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val calibration by vm.calibration.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)

        SectionCard(stringResource(R.string.settings_min_loudness), description = stringResource(R.string.settings_min_loudness_desc)) {
            SettingSlider(
                value = s.minDb,
                onCommit = { v -> vm.update { it.copy(minDb = v.roundToInt().toFloat()) } },
                range = AppSettings.MIN_DB_RANGE_START..AppSettings.MIN_DB_RANGE_END,
                label = stringResource(R.string.settings_min_loudness),
                valueText = { context.getString(R.string.unit_db, it.roundToInt()) },
            )
            if (live.hasSignal) {
                Text(stringResource(R.string.settings_live_level, live.overallDb.roundToInt()), style = MaterialTheme.typography.titleMedium)
            }
        }

        SectionCard(stringResource(R.string.settings_freq_range), description = stringResource(R.string.settings_freq_range_desc)) {
            FrequencyRange(s.minHz, s.maxHz, onCommit = { lo, hi -> vm.update { it.copy(minHz = lo, maxHz = hi) } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hzPresets.forEach { p ->
                    FilterChip(
                        selected = s.minHz.roundToInt() == p.min.roundToInt() && s.maxHz.roundToInt() == p.max.roundToInt(),
                        onClick = { vm.update { it.copy(minHz = p.min, maxHz = p.max) } },
                        label = { Text(stringResource(p.label)) },
                        leadingIcon = if (s.minHz.roundToInt() == p.min.roundToInt() && s.maxHz.roundToInt() == p.max.roundToInt()) {
                            { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.settings_noise_margin), description = stringResource(R.string.settings_noise_margin_desc)) {
            SettingSlider(
                value = s.noiseMarginDb,
                onCommit = { v -> vm.update { it.copy(noiseMarginDb = v.roundToInt().toFloat()) } },
                range = 3f..20f,
                steps = 16,
                label = stringResource(R.string.settings_noise_margin),
                valueText = { context.getString(R.string.unit_db, it.roundToInt()) },
            )
        }

        SectionCard(stringResource(R.string.settings_vibration_strength)) {
            SettingSlider(
                value = s.vibrationStrength,
                onCommit = { v -> vm.update { it.copy(vibrationStrength = v) } },
                range = 0f..1f,
                label = stringResource(R.string.settings_vibration_strength),
                valueText = { context.getString(R.string.unit_percent, (it * 100).roundToInt()) },
            )
            Text(stringResource(R.string.settings_feel_mode), style = MaterialTheme.typography.bodyLarge)
            FeelModeOption(
                selected = !s.naturalFeel,
                title = stringResource(R.string.feel_rhythm),
                description = stringResource(R.string.feel_rhythm_desc),
                onSelect = { vm.update { it.copy(naturalFeel = false) } },
            )
            FeelModeOption(
                selected = s.naturalFeel,
                title = stringResource(R.string.feel_natural),
                description = stringResource(R.string.feel_natural_desc),
                onSelect = { vm.update { it.copy(naturalFeel = true) } },
            )
            ToggleRow(
                title = stringResource(R.string.settings_continuous),
                description = stringResource(R.string.settings_continuous_desc),
                checked = s.continuousEnabled,
                onChange = { v -> vm.update { it.copy(continuousEnabled = v) } },
            )
            OutlinedButton(onClick = vm::testVibration, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.settings_test_vibration))
            }
        }

        SectionCard(stringResource(R.string.settings_calibration), description = stringResource(R.string.settings_calibration_desc)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = vm::calibrate,
                    enabled = calibration != CalibrationState.Measuring,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.settings_calibrate_button)) }
                OutlinedButton(onClick = vm::resetCalibration, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.settings_calibration_reset))
                }
            }
            val msg = when (val c = calibration) {
                CalibrationState.Idle -> null
                CalibrationState.Measuring -> stringResource(R.string.settings_calibrating)
                CalibrationState.NeedsListening -> stringResource(R.string.settings_calibration_needs_listening)
                is CalibrationState.Done -> stringResource(R.string.settings_calibration_done, c.offset.roundToInt())
            }
            if (msg != null) Text(msg, style = MaterialTheme.typography.titleMedium)
        }

        BatteryCard()

        OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.settings_help))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyRange(minHz: Float, maxHz: Float, onCommit: (Float, Float) -> Unit) {
    // Slider works on a log scale so 50–500 Hz is not squeezed into a sliver.
    val lo = kotlin.math.ln(AppSettings.HZ_RANGE_START)
    val hi = kotlin.math.ln(AppSettings.HZ_RANGE_END)
    var range by remember { mutableStateOf(kotlin.math.ln(minHz)..kotlin.math.ln(maxHz)) }
    LaunchedEffect(minHz, maxHz) { range = kotlin.math.ln(minHz)..kotlin.math.ln(maxHz) }
    val shownMin = round10(kotlin.math.exp(range.start))
    val shownMax = round10(kotlin.math.exp(range.endInclusive))
    val text = stringResource(R.string.unit_hz_range, shownMin, shownMax)
    Text(text, style = MaterialTheme.typography.titleMedium)
    RangeSlider(
        value = range,
        onValueChange = { range = it },
        onValueChangeFinished = { onCommit(shownMin.toFloat(), shownMax.toFloat()) },
        valueRange = lo..hi,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = "Frequency range"
                stateDescription = text
            },
    )
}

private fun round10(hz: Float): Int = ((hz / 10f).roundToInt() * 10).coerceIn(50, 8000)

@Composable
private fun FeelModeOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ToggleRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var ignoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { ignoring = isIgnoringBatteryOptimizations(context) }
    }
    SectionCard(stringResource(R.string.settings_battery), description = stringResource(R.string.settings_battery_desc)) {
        if (ignoring) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Text(" " + stringResource(R.string.settings_battery_ok), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Button(onClick = { requestIgnoreBatteryOptimizations(context) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.settings_battery_button))
            }
        }
        OemBatteryTip()
    }
}
