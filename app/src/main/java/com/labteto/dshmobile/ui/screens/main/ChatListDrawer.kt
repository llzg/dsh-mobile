package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.data.SessionRow
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.MenuItem
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hilt entry point used to resolve the process-scoped [SessionStore] singleton. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChatListDrawerEntryPoint {
    fun sessionStore(): SessionStore
}

/** Resolves the process-scoped [SessionStore] once per composition. */
@Composable
private fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current.applicationContext
    return remember {
        EntryPointAccessors.fromApplication(context, ChatListDrawerEntryPoint::class.java).sessionStore()
    }
}

/**
 * Discord-style chat list drawer: new-session action, debounced search,
 * workspace-grouped sessions, archived disclosure, and session/workspace CRUD.
 */
@Composable
fun ChatListDrawer(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = DsTheme.colors
    val store = rememberSessionStore()
    val scope = rememberCoroutineScope()

    val sessions by store.sessions.collectAsStateWithLifecycle()
    val workspaces by store.workspaces.collectAsStateWithLifecycle()
    val archivedIds by store.archivedSessionIds.collectAsStateWithLifecycle()
    val searchResults by store.searchResults.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var newWorkspaceOpen by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(250)
        store.search(query.trim())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.sidebar)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chatlist_title),
                style = DsType.large20,
                color = colors.labelPrimary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = colors.labelTertiary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        DsButton(
            text = stringResource(R.string.chatlist_new_session),
            onClick = {
                scope.launch {
                    store.createSession()
                    onClose()
                }
            },
            variant = DsButtonVariant.Info,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.chatlist_search_hint), style = DsType.std14)
            },
            singleLine = true,
            colors = drawerTextFieldColors(),
        )

        Spacer(Modifier.height(8.dp))

        val activeSessions = sessions.filter { it.sessionId !in archivedIds }
        val workspaceSessionIds = workspaces.flatMap { it.sessionIds }.toSet()
        val archivedSessions = sessions.filter { it.sessionId in archivedIds }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (query.isNotBlank() && searchResults.isNotEmpty()) {
                item(key = "search-header") {
                    SectionHeader(stringResource(R.string.common_search))
                }
                items(searchResults, key = { it.first }) { (sessionId, snippet) ->
                    SearchResultRow(
                        sessionId = sessionId,
                        snippet = snippet,
                        store = store,
                        scope = scope,
                        onClose = onClose,
                    )
                }
            } else {
                for (workspace in workspaces) {
                    val rows = workspace.sessionIds.mapNotNull { id ->
                        activeSessions.firstOrNull { it.sessionId == id }
                    }
                    if (rows.isEmpty()) continue
                    item(key = "workspace-${workspace.workspaceId}") {
                        SectionHeader(workspace.title.ifBlank { workspace.path.substringAfterLast('\\') })
                    }
                    items(rows, key = { it.sessionId }) { session ->
                        SessionRowItem(session, store, scope, onClose)
                    }
                }

                val ungrouped = activeSessions.filter { it.sessionId !in workspaceSessionIds }
                if (ungrouped.isNotEmpty()) {
                    item(key = "sessions-header") {
                        SectionHeader(stringResource(R.string.chatlist_sessions))
                    }
                    items(ungrouped, key = { it.sessionId }) { session ->
                        SessionRowItem(session, store, scope, onClose)
                    }
                }

                if (archivedSessions.isNotEmpty()) {
                    item(key = "archived") {
                        var archivedExpanded by remember { mutableStateOf(false) }
                        DisclosureRow(
                            title = stringResource(R.string.chatlist_archived),
                            expanded = archivedExpanded,
                            onToggle = { archivedExpanded = !archivedExpanded },
                        ) {
                            archivedSessions.forEach { session ->
                                SessionRowItem(session, store, scope, onClose)
                            }
                        }
                    }
                }

                if (sessions.isEmpty()) {
                    item(key = "empty") {
                        EmptyHero(
                            headline = stringResource(R.string.chatlist_empty),
                            subtitle = stringResource(R.string.chatlist_empty_hint),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { newWorkspaceOpen = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(R.string.chatlist_new_workspace))
        }
    }

    if (newWorkspaceOpen) {
        var pathText by remember { mutableStateOf("") }
        DsDialog(
            title = stringResource(R.string.chatlist_new_workspace),
            onDismiss = { newWorkspaceOpen = false },
        ) {
            TextField(
                value = pathText,
                onValueChange = { pathText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.chatlist_workspace_path), style = DsType.std14)
                },
                singleLine = true,
                colors = drawerTextFieldColors(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { newWorkspaceOpen = false },
                    variant = DsButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                DsButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        scope.launch {
                            store.createWorkspace(pathText.trim())
                            newWorkspaceOpen = false
                        }
                    },
                    variant = DsButtonVariant.Info,
                )
            }
        }
    }
}

/** One session row: status dot, title, caption, optional subagent pill, long-press menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowItem(
    session: SessionRow,
    store: SessionStore,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val colors = DsTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var archiveConfirmOpen by remember { mutableStateOf(false) }

    val menuItems = listOf(
        MenuItem(
            text = stringResource(R.string.chatlist_session_rename),
            onClick = {
                menuOpen = false
                renameOpen = true
            },
        ),
        MenuItem(
            text = stringResource(R.string.chatlist_session_fork),
            onClick = {
                menuOpen = false
                scope.launch { store.forkSession(session.sessionId) }
            },
        ),
        MenuItem(
            text = stringResource(R.string.chatlist_session_archive),
            danger = true,
            onClick = {
                menuOpen = false
                archiveConfirmOpen = true
            },
        ),
    )

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            store.openSession(session.sessionId)
                            onClose()
                        }
                    },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateDot(
                state = when {
                    session.running -> StateDotState.Running
                    session.pendingInteraction != null -> StateDotState.Warning
                    else -> StateDotState.Idle
                },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = sessionTitle(session),
                    style = DsType.std14,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val caption = sessionCaption(session)
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = DsType.caption11,
                        color = colors.labelTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (session.origin == "subagent") {
                Spacer(Modifier.width(6.dp))
                DsPill(
                    text = stringResource(R.string.chatlist_subagents),
                    warn = false,
                )
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = DsShapes.menu,
            containerColor = colors.bgLayer3,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, colors.borderL1),
        ) {
            menuItems.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.text,
                            style = DsType.std14,
                            color = if (item.danger) colors.error else colors.labelPrimary,
                        )
                    },
                    onClick = item.onClick,
                )
            }
        }
    }

    if (renameOpen) {
        var titleText by remember(session.sessionId) { mutableStateOf(session.title.orEmpty()) }
        DsDialog(
            title = stringResource(R.string.chatlist_session_rename),
            onDismiss = { renameOpen = false },
        ) {
            TextField(
                value = titleText,
                onValueChange = { titleText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.chatlist_session_rename), style = DsType.std14)
                },
                singleLine = true,
                colors = drawerTextFieldColors(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { renameOpen = false },
                    variant = DsButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                DsButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        scope.launch {
                            store.renameSession(session.sessionId, titleText.trim())
                            renameOpen = false
                        }
                    },
                    variant = DsButtonVariant.Info,
                )
            }
        }
    }

    if (archiveConfirmOpen) {
        DsDialog(
            title = stringResource(R.string.chatlist_session_archive),
            onDismiss = { archiveConfirmOpen = false },
        ) {
            Text(
                text = sessionTitle(session),
                style = DsType.std14,
                color = colors.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { archiveConfirmOpen = false },
                    variant = DsButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                DsButton(
                    text = stringResource(R.string.common_archive),
                    onClick = {
                        scope.launch {
                            store.archiveSession(session.sessionId)
                            archiveConfirmOpen = false
                        }
                    },
                    variant = DsButtonVariant.Danger,
                )
            }
        }
    }
}

/** One search result row: two-line snippet plus the session id caption. */
@Composable
private fun SearchResultRow(
    sessionId: String,
    snippet: String,
    store: SessionStore,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    store.openSession(sessionId)
                    onClose()
                }
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = snippet,
                style = DsType.rowText,
                color = colors.labelTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sessionId,
                style = DsType.caption11,
                color = colors.labelCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shared input-field palette: layer-1 container, accent/border indicator. */
@Composable
private fun drawerTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = DsTheme.colors.bgLayer1,
    unfocusedContainerColor = DsTheme.colors.bgLayer1,
    focusedIndicatorColor = DsTheme.colors.accent,
    unfocusedIndicatorColor = DsTheme.colors.borderL2,
    cursorColor = DsTheme.colors.accent,
)

/** Display title: explicit title, else the cwd's trailing folder, else the session id. */
private fun sessionTitle(session: SessionRow): String {
    val title = session.title?.takeIf { it.isNotBlank() }
    val folder = session.cwd
        ?.takeIf { it.isNotBlank() }
        ?.substringAfterLast('\\')
        ?.takeIf { it.isNotBlank() }
    return title ?: folder ?: session.sessionId
}

/** Caption: cwd (when present) plus the last-update clock time. */
private fun sessionCaption(session: SessionRow): String {
    val time = SimpleDateFormat("HH:mm").format(Date(session.updatedAt))
    val cwd = session.cwd?.takeIf { it.isNotBlank() }
    return if (cwd == null) time else "$cwd · $time"
}
