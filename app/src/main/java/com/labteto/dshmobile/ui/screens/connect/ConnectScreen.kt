package com.labteto.dshmobile.ui.screens.connect

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.labteto.dshmobile.connection.DiscoveredHost
import com.labteto.dshmobile.connection.HostConfig
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

@Composable
fun ConnectScreen(onOpenSettings: () -> Unit, viewModel: ConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = DsTheme.colors
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3080") }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = colors.labelTertiary)
                }
            }

            EmptyHero(
                headline = stringResource(R.string.app_long_name),
                subtitle = stringResource(R.string.connect_subtitle),
                chips = emptyList(),
                onChipClick = {},
            )

            Spacer(Modifier.height(8.dp))

            // Security banner (always on the connect screen).
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colors.warnTertiary,
            ) {
                Text(
                    stringResource(R.string.connect_security_banner),
                    style = DsType.small13,
                    color = colors.warnLabel,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader(stringResource(R.string.connect_manual_title))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.connect_host_hint), style = DsType.std14) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.connect_host_label)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.bgLayer1,
                        unfocusedContainerColor = colors.bgLayer1,
                        focusedIndicatorColor = colors.accent,
                        unfocusedIndicatorColor = colors.borderL2,
                        cursorColor = colors.accent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.width(92.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.connect_port_label)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.bgLayer1,
                        unfocusedContainerColor = colors.bgLayer1,
                        focusedIndicatorColor = colors.accent,
                        unfocusedIndicatorColor = colors.borderL2,
                        cursorColor = colors.accent,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            DsButton(
                text = stringResource(R.string.connect_button),
                onClick = { viewModel.connectManual(host, port) },
                enabled = !state.connecting,
                variant = DsButtonVariant.Info,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (state.error) {
                        "connect_failed" -> stringResource(R.string.connect_failed, host, port)
                        "connect_failed_fence" -> stringResource(R.string.connect_failed_fence)
                        else -> stringResource(R.string.common_error)
                    },
                    style = DsType.std14,
                    color = colors.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader(
                title = stringResource(R.string.connect_auto_title),
            )
            AutoToggle(stringResource(R.string.connect_auto_last), state.autoConnectLast) {
                viewModel.setAuto("last", it)
            }
            AutoToggle(stringResource(R.string.connect_auto_lan), state.autoConnectLan) {
                viewModel.setAuto("lan", it)
            }
            AutoToggle(stringResource(R.string.connect_auto_loopback), state.autoConnectLoopback) {
                viewModel.setAuto("loopback", it)
            }

            if (state.remembered.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(stringResource(R.string.connect_remembered))
                Spacer(Modifier.height(6.dp))
                state.remembered.forEach { saved ->
                    SavedHostRow(
                        host = saved,
                        onConnect = { viewModel.connectTo(saved) },
                        onDelete = { viewModel.forget(saved) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(
                title = stringResource(R.string.connect_discovered),
                action = stringResource(R.string.connect_scan),
                onAction = { viewModel.scan() },
            )
            Spacer(Modifier.height(6.dp))
            when {
                state.scanning -> Text(
                    stringResource(R.string.connect_scanning),
                    style = DsType.std14,
                    color = colors.labelTertiary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                state.discovered.isEmpty() -> Text(
                    stringResource(R.string.connect_discovered_hint),
                    style = DsType.std14,
                    color = colors.labelCaption,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                else -> state.discovered.forEach { found ->
                    DiscoveredRow(found) { viewModel.connectDiscovered(found) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AutoToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DsType.std14, color = colors.labelSecondary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SavedHostRow(host: HostConfig, onConnect: () -> Unit, onDelete: () -> Unit) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Lan,
            contentDescription = null,
            tint = colors.labelTertiary,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(host.name, style = DsType.std14, color = colors.labelPrimary)
            Text(host.authority, style = DsType.caption11, color = colors.labelCaption)
        }
        DsButton(text = stringResource(R.string.connect_button), onClick = onConnect, variant = DsButtonVariant.Ghost, size = DsButtonSize.Small)
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = colors.labelCaption)
        }
    }
}

@Composable
private fun DiscoveredRow(host: DiscoveredHost, onConnect: () -> Unit) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lan, contentDescription = null, tint = colors.accent)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${host.host}:${host.port}", style = DsType.std14, color = colors.labelPrimary)
            Text(
                stringResource(R.string.connect_harness_version, host.version, host.cwd),
                style = DsType.caption11,
                color = colors.labelCaption,
                maxLines = 1,
            )
        }
        DsButton(text = stringResource(R.string.connect_button), onClick = onConnect, variant = DsButtonVariant.Info, size = DsButtonSize.Small)
    }
}
