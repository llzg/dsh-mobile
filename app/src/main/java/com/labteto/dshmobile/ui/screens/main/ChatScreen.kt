package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
 * The chat surface: streamed conversation + composer + dock cards
 * (todo/goal/queue). Full implementation lands with the session store.
 */
@Composable
fun ChatScreen(
    onOpenDetails: () -> Unit,
    detailsOpen: Boolean,
) {
    val colors = DsTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.common_loading), style = DsType.std14, color = colors.labelTertiary)
            }
            if (!detailsOpen) {
                IconButton(
                    onClick = onOpenDetails,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.chat_details_title), tint = colors.labelTertiary)
                }
            }
        }
    }
}
