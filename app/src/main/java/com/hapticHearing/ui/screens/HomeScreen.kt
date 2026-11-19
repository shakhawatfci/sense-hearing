package com.hapticHearing.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hapticHearing.R
import com.hapticHearing.data.settings.AppSettings
import com.hapticHearing.data.settings.SettingsRepository
import com.hapticHearing.service.HearingPipeline
import com.hapticHearing.service.ListeningStatus
import com.hapticHearing.service.LiveState
import com.hapticHearing.service.ServiceController
import com.hapticHearing.ui.components.BandMeter
import com.hapticHearing.ui.components.BandMeterLegend
import com.hapticHearing.ui.components.HSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    pipeline: HearingPipeline,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val live: StateFlow<LiveState> = pipeline.live
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setListening(on: Boolean) {
        if (on) ServiceController.start(context) else ServiceController.stop(context)
    }

    fun setQuietMode(on: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(quietMode = on) }
    }
}

private fun hasMic(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micDenied by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) {
            vm.setListening(true)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun toggle(on: Boolean) {
        if (on && !hasMic(context)) micLauncher.launch(Manifest.permission.RECORD_AUDIO) else vm.setListening(on)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ListeningSwitch(live, onToggle = ::toggle)

        if (micDenied || live.status == ListeningStatus.NO_PERMISSION) {
            MicPermissionCard(onRetry = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) })
        }

        LevelCard(live, settings)
        NowHearingCard()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.quietMode,
                onClick = { vm.setQuietMode(!settings.quietMode) },
                label = { Text(stringResource(R.string.chip_quiet_mode)) },
                leadingIcon = { Icon(Icons.Filled.NotificationsPaused, contentDescription = null) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
            AssistChip(
                onClick = onOpenSettings,
                label = { Text(stringResource(R.string.chip_sensitivity)) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun ListeningSwitch(live: LiveState, onToggle: (Boolean) -> Unit) {
    val on = live.isOn
    val statusText = stringResource(
        when (live.status) {
            ListeningStatus.OFF -> R.string.status_off
            ListeningStatus.STARTING -> R.string.status_starting
            ListeningStatus.LISTENING -> R.string.status_listening
            ListeningStatus.PAUSED -> R.string.status_paused
            ListeningStatus.MIC_BUSY -> R.string.status_mic_busy
            ListeningStatus.IN_CALL -> R.string.status_in_call
            ListeningStatus.NO_PERMISSION -> R.string.status_no_permission
        },
    )
    val warn = live.status in setOf(ListeningStatus.MIC_BUSY, ListeningStatus.IN_CALL, ListeningStatus.NO_PERMISSION)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle),
    ) {
        Row(
            Modifier
                .padding(20.dp)
                .heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    warn -> Icons.Filled.Warning
                    on -> Icons.Filled.Hearing
                    else -> Icons.Filled.HearingDisabled
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            HSpace(16)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (on) R.string.listening else R.string.not_listening),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            // The whole card is the toggle; the Switch is visual only.
            Switch(checked = on, onCheckedChange = null)
        }
    }
}

@Composable
private fun LevelCard(live: LiveState, settings: AppSettings) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (live.hasSignal) stringResource(R.string.db_approx, live.overallDb.roundToInt()) else "≈ — dB",
                fontSize = 56.sp,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            BandMeter(
                bandDb = live.bandDb,
                floorDb = live.floorDb,
                active = live.active,
                edgesHz = live.bandEdgesHz,
                minDb = settings.minDb,
                noiseMarginDb = settings.noiseMarginDb,
            )
            BandMeterLegend()
        }
    }
}

@Composable
private fun NowHearingCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.now_hearing), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.now_hearing_empty), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MicPermissionCard(onRetry: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.mic_permission_rationale), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.grant_permission))
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.open_app_settings)) }
            }
        }
    }
}
