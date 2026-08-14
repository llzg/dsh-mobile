package com.labteto.dshmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.connection.ConnectionPhase
import com.labteto.dshmobile.ui.screens.connect.ConnectScreen
import com.labteto.dshmobile.ui.screens.main.MainScreen
import com.labteto.dshmobile.ui.screens.settings.SettingsScreen
import com.labteto.dshmobile.ui.theme.DshTheme
import com.labteto.dshmobile.ui.theme.ThemePreference

/** Application root: theme + locale-aware shell, connect vs. main routing. */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val themePreference = remember(settings.themePreference) {
        runCatching { ThemePreference.valueOf(settings.themePreference.uppercase()) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    DshTheme(preference = themePreference) {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        if (showSettings) {
            SettingsScreen(onClose = { showSettings = false })
        } else {
            val showMain = connection.phase == ConnectionPhase.CONNECTED ||
                (connection.phase == ConnectionPhase.RECONNECTING && connection.hasConnected)
            if (showMain) {
                MainScreen(onOpenSettings = { showSettings = true })
            } else {
                ConnectScreen(onOpenSettings = { showSettings = true })
            }
        }
    }
}
