package com.hapticHearing.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hapticHearing.R
import com.hapticHearing.ui.components.BackTopBar
import com.hapticHearing.ui.components.SectionCard

/** Screens whose features land in later milestones (M4–M6). They navigate and explain what is coming. */
@Composable
private fun ComingSoon(icon: ImageVector, title: String, body: String, milestone: String, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(96.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(stringResource(R.string.coming_in, milestone), style = MaterialTheme.typography.titleMedium)
        extra()
    }
}

@Composable
fun FamilyScreen(onOpenMe: () -> Unit) = ComingSoon(
    Icons.Filled.FamilyRestroom,
    stringResource(R.string.nav_family),
    stringResource(R.string.family_placeholder),
    "M5",
) {
    OutlinedButton(onClick = onOpenMe, modifier = Modifier.heightIn(min = 56.dp)) {
        Icon(Icons.Filled.Person, contentDescription = null)
        Text(" " + stringResource(R.string.nav_me))
    }
}

@Composable
fun SoundsScreen() = ComingSoon(
    Icons.Filled.NotificationsActive,
    stringResource(R.string.nav_sounds),
    stringResource(R.string.sounds_placeholder),
    "M4",
)

@Composable
fun HistoryScreen() = ComingSoon(
    Icons.Filled.History,
    stringResource(R.string.nav_history),
    stringResource(R.string.history_placeholder),
    "M4",
)

@Composable
fun MeScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.nav_me), onBack) }) { p ->
        Column(Modifier.padding(p)) {
            ComingSoon(Icons.Filled.Person, stringResource(R.string.nav_me), stringResource(R.string.me_placeholder), "M6")
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.help_title), onBack) }) { p ->
        Column(
            Modifier
                .padding(p)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(stringResource(R.string.onb_title_3)) {
                Text(stringResource(R.string.help_safety), style = MaterialTheme.typography.bodyLarge)
            }
            SectionCard(stringResource(R.string.help_limits_title)) {
                listOf(R.string.help_limit_1, R.string.help_limit_2, R.string.help_limit_3, R.string.help_limit_4).forEach {
                    Text("• " + stringResource(it), style = MaterialTheme.typography.bodyLarge)
                }
            }
            SectionCard(stringResource(R.string.help_battery_title)) {
                OemBatteryTip()
            }
        }
    }
}

/**
 * Manufacturer-specific steps for aggressive battery killers (spec §12). Kept as plain English text
 * for now; translated in M7.
 */
@Composable
fun OemBatteryTip(modifier: Modifier = Modifier) {
    val tip = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" ->
            "Xiaomi: Settings → Apps → Haptic Hearing → Battery saver → No restrictions. Also turn on Autostart."
        "samsung" ->
            "Samsung: Settings → Battery → Background usage limits → Never sleeping apps → add Haptic Hearing."
        "huawei", "honor" ->
            "Huawei/Honor: Settings → Battery → App launch → Haptic Hearing → Manage manually, turn all three on."
        "oneplus" ->
            "OnePlus: Settings → Battery → Battery optimisation → Haptic Hearing → Don't optimise."
        "oppo", "realme" ->
            "Oppo/Realme: Settings → Battery → Haptic Hearing → Allow background activity."
        "vivo", "iqoo" ->
            "Vivo: Settings → Battery → Background power consumption → Haptic Hearing → Allow."
        else ->
            "If listening stops with the screen off, allow background running above and lock the app in the recent-apps screen."
    }
    Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = modifier.fillMaxWidth())
}
