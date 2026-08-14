package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The Discord-style chat list drawer: workspace-grouped sessions.
 * Full implementation (session rows, status dots, search, workspace CRUD)
 * lands with the session store; this is the shell.
 */
@Composable
fun ChatListDrawer(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = DsTheme.colors
    Surface(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight(),
        color = colors.sidebar,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chatlist_title), style = DsType.large20, color = colors.labelPrimary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = colors.labelTertiary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.common_loading),
                style = DsType.std14,
                color = colors.labelTertiary,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
