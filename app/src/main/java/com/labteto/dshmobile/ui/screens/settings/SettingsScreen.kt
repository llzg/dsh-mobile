package com.labteto.dshmobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.connection.AppSettings
import com.labteto.dshmobile.connection.ConnectionPhase
import com.labteto.dshmobile.core.DshCore
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

@Composable
fun SettingsScreen(onClose: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val colors = DsTheme.colors
    var showDisconnectDialog by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.labelSecondary)
                }
                Text(stringResource(R.string.settings_title), style = DsType.large20, color = colors.labelPrimary)
            }
            Spacer(Modifier.height(8.dp))

            SectionHeader(stringResource(R.string.settings_general))
            Spacer(Modifier.height(4.dp))
            LanguageRow(settings) { tag -> viewModel.set { it.copy(localeOverride = tag) } }
            AppearanceRow(settings) { mode -> viewModel.set { it.copy(themePreference = mode) } }

            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.settings_connection))
            Spacer(Modifier.height(4.dp))
            ConnectionSection(connectionState, onDisconnect = { showDisconnectDialog = true })

            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.settings_notifications))
            ToggleRow(stringResource(R.string.settings_notifications_turn), settings.notifyTurnComplete) {
                viewModel.set { it.copy(notifyTurnComplete = !it.notifyTurnComplete) }
            }
            ToggleRow(stringResource(R.string.settings_notifications_goal), settings.notifyGoal) {
                viewModel.set { it.copy(notifyGoal = !it.notifyGoal) }
            }
            ToggleRow(stringResource(R.string.settings_notifications_action), settings.notifyNeedsAction) {
                viewModel.set { it.copy(notifyNeedsAction = !it.notifyNeedsAction) }
            }
            ToggleRow(stringResource(R.string.settings_background), settings.keepConnectedInBackground) {
                viewModel.set { it.copy(keepConnectedInBackground = !it.keepConnectedInBackground) }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_about_version, "0.1.0", DshCore.PROTOCOL_BASELINE),
                style = DsType.small13,
                color = colors.labelTertiary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                stringResource(R.string.settings_readonly_banner),
                style = DsType.small13,
                color = colors.warnLabel,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDisconnectDialog) {
        DsDialog(
            title = stringResource(R.string.settings_connection_disconnect_confirm),
            onDismiss = { showDisconnectDialog = false }
        ) {
            Text(
                stringResource(R.string.settings_connection_disconnect_message),
                style = DsType.std14,
                color = colors.labelSecondary,
                modifier = Modifier.padding(bottom = DsSpacing.medium)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                DsButton(
                    text = stringResource(R.string.settings_connection_disconnect),
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectDialog = false
                        onClose()
                    },
                    variant = DsButtonVariant.Danger,
                )
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { showDisconnectDialog = false },
                    variant = DsButtonVariant.Ghost,
                )
            }
        }
    }
}

@Composable
private fun ConnectionSection(
    connectionState: com.labteto.dshmobile.connection.ConnectionUiState,
    onDisconnect: () -> Unit
) {
    val colors = DsTheme.colors
    val isConnected = connectionState.phase == ConnectionPhase.CONNECTED || 
                     connectionState.phase == ConnectionPhase.RECONNECTING

    Column(modifier = Modifier.padding(vertical = DsSpacing.compact)) {
        // Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_connection_status),
                style = DsType.std14,
                color = colors.labelSecondary,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(
                    when (connectionState.phase) {
                        ConnectionPhase.CONNECTED -> StateDotState.Done
                        ConnectionPhase.RECONNECTING -> StateDotState.Running
                        ConnectionPhase.CONNECTING -> StateDotState.Running
                        else -> StateDotState.Idle
                    }
                )
                Spacer(Modifier.width(DsSpacing.small))
                Text(
                    when (connectionState.phase) {
                        ConnectionPhase.CONNECTED -> stringResource(R.string.common_connected)
                        ConnectionPhase.RECONNECTING -> stringResource(R.string.common_reconnecting)
                        ConnectionPhase.CONNECTING -> stringResource(R.string.common_loading)
                        else -> stringResource(R.string.common_offline)
                    },
                    style = DsType.small13,
                    color = colors.labelTertiary
                )
            }
        }

        // Host info row
        if (isConnected && connectionState.host != null) {
            Spacer(Modifier.height(DsSpacing.compact))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_connection_host),
                    style = DsType.std14,
                    color = colors.labelSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    connectionState.host.authority,
                    style = DsType.small13,
                    color = colors.labelTertiary
                )
            }
        }

        // Disconnect button
        if (isConnected) {
            Spacer(Modifier.height(DsSpacing.medium))
            DsButton(
                text = stringResource(R.string.settings_connection_disconnect),
                onClick = onDisconnect,
                variant = DsButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: () -> Unit) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DsType.std14, color = colors.labelSecondary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onChange() })
    }
}

@Composable
private fun LanguageRow(settings: AppSettings, onSelect: (String) -> Unit) {
    val colors = DsTheme.colors
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(stringResource(R.string.settings_language), style = DsType.std14, color = colors.labelSecondary)
        Spacer(Modifier.height(8.dp))
        LanguageOptions.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val selected = settings.localeOverride == option.tag
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .clickable { onSelect(option.tag) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) colors.accentTertiary else colors.bgModulePlatform,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option.label,
                                style = DsType.small13,
                                color = if (selected) colors.accent else colors.labelSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.width(14.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AppearanceRow(settings: AppSettings, onSelect: (String) -> Unit) {
    val colors = DsTheme.colors
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(stringResource(R.string.settings_appearance), style = DsType.std14, color = colors.labelSecondary)
        Spacer(Modifier.height(8.dp))
        Row {
            AppearanceChip(stringResource(R.string.settings_appearance_light), settings.themePreference == "light") { onSelect("light") }
            Spacer(Modifier.width(8.dp))
            AppearanceChip(stringResource(R.string.settings_appearance_dark), settings.themePreference == "dark") { onSelect("dark") }
            Spacer(Modifier.width(8.dp))
            AppearanceChip(stringResource(R.string.settings_appearance_system), settings.themePreference == "system") { onSelect("system") }
        }
    }
}

@Composable
private fun AppearanceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DsTheme.colors
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = DsShapes.cube,
        color = if (selected) colors.accentTertiary else colors.bgModulePlatform,
    ) {
        Text(
            label,
            style = DsType.small13,
            color = if (selected) colors.accent else colors.labelSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
