package com.hapticHearing.ui.screens

import android.Manifest
import android.app.NotificationManager
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.hapticHearing.R
import com.hapticHearing.data.settings.SettingsRepository
import com.hapticHearing.ui.components.HSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        repo.update { it.copy(onboardingDone = true) }
        onDone()
    }
}

private class Page(val icons: List<ImageVector>, val title: Int, val body: Int)

private val pages = listOf(
    Page(listOf(Icons.Filled.Hearing, Icons.Filled.GraphicEq, Icons.Filled.Vibration), R.string.onb_title_1, R.string.onb_body_1),
    Page(listOf(Icons.Filled.Air, Icons.Filled.Vibration), R.string.onb_title_2, R.string.onb_body_2),
    Page(listOf(Icons.Filled.Warning), R.string.onb_title_3, R.string.onb_body_3),
    Page(listOf(Icons.Filled.PhoneAndroid, Icons.Filled.Lock, Icons.Filled.WifiOff), R.string.onb_title_4, R.string.onb_body_4),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    if (page < pages.size) {
        InfoPage(pages[page], onNext = { page++ }, onSkip = { page = pages.size })
    } else {
        PermissionsPage(onDone = { vm.finish(onFinished) })
    }
}

@Composable
private fun InfoPage(p: Page, onNext: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            p.icons.forEach { Icon(it, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Text(stringResource(p.title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(stringResource(p.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.heightIn(min = 16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.skip)) }
    }
}

private fun granted(context: Context, perm: String) =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

private fun canFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

@Composable
private fun PermissionsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    // `tick` forces re-evaluation after returning from a dialog or system settings.
    val micOk = remember(tick) { granted(context, Manifest.permission.RECORD_AUDIO) }
    val notifOk = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS) }
    val fullOk = remember(tick) { canFullScreen(context) }
    val batteryOk = remember(tick) { isIgnoringBatteryOptimizations(context) }
    val cameraOk = remember(tick) { granted(context, Manifest.permission.CAMERA) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.onb_permissions), style = MaterialTheme.typography.headlineLarge)
        PermissionRow(Icons.Filled.Mic, R.string.onb_perm_mic, R.string.onb_perm_mic_desc, micOk) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(Icons.Filled.Notifications, R.string.onb_perm_notif, R.string.onb_perm_notif_desc, notifOk) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            PermissionRow(Icons.Filled.ScreenLockPortrait, R.string.onb_perm_fullscreen, R.string.onb_perm_fullscreen_desc, fullOk) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                )
            }
        }
        PermissionRow(Icons.Filled.BatteryChargingFull, R.string.onb_perm_battery, R.string.onb_perm_battery_desc, batteryOk) {
            @Suppress("BatteryLife")
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            )
        }
        PermissionRow(Icons.Filled.CameraAlt, R.string.onb_perm_camera, R.string.onb_perm_camera_desc, cameraOk) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        Button(
            onClick = onDone,
            enabled = micOk,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium) }
        if (!micOk) {
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.skip)) }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: Int, desc: Int, ok: Boolean, onAllow: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            HSpace(12)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
            }
            HSpace(8)
            if (ok) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.onb_granted), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(onClick = onAllow, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.onb_allow)) }
            }
        }
    }
}
