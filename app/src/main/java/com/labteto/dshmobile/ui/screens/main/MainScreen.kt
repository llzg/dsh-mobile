package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * Discord-style shell:
 *  - swipe right from the LEFT edge opens the chat-list drawer
 *  - swipe left on the drawer content closes it (ModalNavigationDrawer default)
 *  - swipe left from the RIGHT edge opens the session Details panel
 *  - swipe right on the details panel closes it (also Back).
 */
@Composable
fun MainScreen(onOpenSettings: () -> Unit, viewModel: MainViewModel = hiltViewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var detailsOpen by remember { mutableStateOf(false) }
    val edgeWidth = with(LocalDensity.current) { 28.dp.toPx() }
    val detailsWidth = 300.dp

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatListDrawer(
                onClose = { scope.launch { drawerState.close() } },
                onOpenSettings = onOpenSettings,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChatScreen(
                onOpenDetails = { detailsOpen = true },
                detailsOpen = detailsOpen,
            )

            AnimatedVisibility(
                visible = detailsOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                DetailsPanel(
                    onClose = { detailsOpen = false },
                    modifier = Modifier.width(detailsWidth),
                )
            }
        }
    }
}
