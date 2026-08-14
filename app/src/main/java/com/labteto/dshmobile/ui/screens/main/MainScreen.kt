package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 *    (ModalNavigationDrawer's built-in gesture; swipe left on the drawer
 *    content closes it, scrim tap and Back also work)
 *  - swipe left from the RIGHT edge opens the session Details panel
 *  - swipe right anywhere on the open Details panel closes it
 *
 * Horizontal edge drags are axis-orthogonal to the chat list's vertical
 * scroll, so the two never conflict.
 */
@Composable
fun MainScreen(onOpenSettings: () -> Unit, viewModel: MainViewModel = hiltViewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var detailsOpen by remember { mutableStateOf(false) }
    val edgeWidthPx = with(LocalDensity.current) { 28.dp.toPx() }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detailsOpen) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = { },
                        onDragCancel = { },
                    ) { change, dragAmount ->
                        val width = size.width.toFloat()
                        if (!detailsOpen && dragAmount < 0) {
                            // Right-edge swipe left opens details.
                            val startX = change.position.x - dragAmount
                            if (startX >= width - edgeWidthPx && -dragAmount > width * 0.12f) {
                                detailsOpen = true
                            }
                        } else if (detailsOpen && dragAmount > 0) {
                            // Swipe right across the details area closes it.
                            val startX = change.position.x - dragAmount
                            if (startX <= detailsWidth.toPx() * 0.9f && dragAmount > width * 0.12f) {
                                detailsOpen = false
                            }
                        }
                        change.consume()
                    }
                },
        ) {
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
