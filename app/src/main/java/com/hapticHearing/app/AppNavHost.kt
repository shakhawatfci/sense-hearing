package com.hapticHearing.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hapticHearing.R
import com.hapticHearing.data.settings.SettingsRepository
import com.hapticHearing.ui.screens.FamilyScreen
import com.hapticHearing.ui.screens.HelpScreen
import com.hapticHearing.ui.screens.HistoryScreen
import com.hapticHearing.ui.screens.HomeScreen
import com.hapticHearing.ui.screens.MeScreen
import com.hapticHearing.ui.screens.OnboardingScreen
import com.hapticHearing.ui.screens.SettingsScreen
import com.hapticHearing.ui.screens.SoundsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val FAMILY = "family"
    const val SOUNDS = "sounds"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ME = "me"
    const val HELP = "help"
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.GraphicEq),
    TopLevel(Routes.FAMILY, R.string.nav_family, Icons.Filled.FamilyRestroom),
    TopLevel(Routes.SOUNDS, R.string.nav_sounds, Icons.Filled.NotificationsActive),
    TopLevel(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val onboardingDone: StateFlow<Boolean?> =
        settings.settings.map { it.onboardingDone }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun AppNavHost(vm: AppViewModel = hiltViewModel()) {
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val done = onboardingDone ?: run {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = topLevel.any { it.route == current }

    fun goTop(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.route,
                            onClick = { goTop(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (done) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) { HomeScreen(onOpenSettings = { goTop(Routes.SETTINGS) }) }
            composable(Routes.FAMILY) { FamilyScreen(onOpenMe = { nav.navigate(Routes.ME) }) }
            composable(Routes.SOUNDS) { SoundsScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenHelp = { nav.navigate(Routes.HELP) }) }
            composable(Routes.ME) { MeScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.HELP) { HelpScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
