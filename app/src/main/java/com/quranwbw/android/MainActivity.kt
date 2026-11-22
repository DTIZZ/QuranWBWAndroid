package com.quranwbw.android

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quranwbw.android.ui.screens.QiblaScreen
import com.quranwbw.android.ui.screens.QuranStatsScreen
import com.quranwbw.android.ui.screens.ReaderScreen
import com.quranwbw.android.ui.screens.SettingsScreen
import com.quranwbw.android.ui.screens.TodayScreen
import com.quranwbw.android.ui.theme.QuranWBWTheme
import com.quranwbw.android.ui.viewmodel.ReaderViewModel

private enum class AppTab(val label: String, val icon: ImageVector) {
    ADHAN("Adhan", Icons.Filled.AccessTime),
    QURAN("Qur'an", Icons.AutoMirrored.Filled.MenuBook),
    QIBLA("Qibla", Icons.Filled.Explore),
    STATS("Stats", Icons.Filled.BarChart),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuranWBWTheme {
                val vm: ReaderViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                var currentTab by rememberSaveable { mutableStateOf(AppTab.ADHAN) }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        vm.useGpsLocation()
                    } else {
                        currentTab = AppTab.SETTINGS
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = {
                                        if (tab != AppTab.QURAN) {
                                            vm.stopPlayback()
                                        }
                                        currentTab = tab
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)

                    when (currentTab) {
                        AppTab.QURAN -> ReaderScreen(
                            uiState = uiState,
                            onSelectSurah = vm::selectSurah,
                            onWordClick = vm::selectAndPlayWord,
                            onReplayWord = vm::replaySelectedWord,
                            onLetterClick = vm::selectAndPlayLetter,
                            onToggleAyahPlay = vm::togglePlayAyah,
                            onToggleSpeedReadMode = vm::toggleSpeedReadMode,
                            onSpeedReadWpmChange = vm::setSpeedReadWpm,
                            onSpeedReadThemeChange = vm::setSpeedReadTheme,
                            onFocusedReadModeChange = vm::setFocusedReadMode,
                            onSpeedReadAyahCompleted = vm::recordAyahCompleted,
                            modifier = contentModifier,
                        )

                        AppTab.ADHAN -> TodayScreen(
                            settings = uiState.settings,
                            weather = uiState.weather,
                            isRefreshing = uiState.isBusy,
                            statusMessage = uiState.statusMessage,
                            onRefresh = vm::refreshWeatherAndLocation,
                            onUseGps = {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            onOpenSettings = { currentTab = AppTab.SETTINGS },
                            onOpenQibla = { currentTab = AppTab.QIBLA },
                            onToggleAlert = vm::togglePrayerAlert,
                            modifier = contentModifier,
                        )

                        AppTab.QIBLA -> QiblaScreen(
                            settings = uiState.settings,
                            onOpenSettings = { currentTab = AppTab.SETTINGS },
                            modifier = contentModifier,
                        )

                        AppTab.STATS -> QuranStatsScreen(
                            surahs = uiState.surahs,
                            settings = uiState.settings,
                            ayahReads = uiState.ayahReads,
                            sessions = uiState.sessions,
                            modifier = contentModifier,
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            settings = uiState.settings,
                            isBusy = uiState.isBusy,
                            statusMessage = uiState.statusMessage,
                            onUpdateSettings = vm::updateSettings,
                            onUseGps = {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            onUsePostalCode = vm::usePostalCode,
                            onSelectPresetLocation = vm::selectPresetLocation,
                            onClearReadingStats = vm::clearReadingStats,
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}
