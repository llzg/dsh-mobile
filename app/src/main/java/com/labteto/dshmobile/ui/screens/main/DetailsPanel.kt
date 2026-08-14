package com.labteto.dshmobile.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * Right-hand session details panel (goal / plan / jobs / queue / trajectory
 * overview) — the Discord "members panel" analog. Full content lands with the
 * session store.
 */
@Composable
fun DetailsPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val colors = DsTheme.colors
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = colors.bgLayer1,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.labelSecondary)
                }
                Text(stringResource(R.string.chat_details_title), style = DsType.std14Strong, color = colors.labelPrimary)
            }
            Text(
                stringResource(R.string.chat_details_empty),
                style = DsType.small13,
                color = colors.labelTertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
