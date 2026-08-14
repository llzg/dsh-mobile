package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.CommandNode
import com.labteto.dshmobile.core.session.CompactionNode
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.core.session.GoalNode
import com.labteto.dshmobile.core.session.OtherNode
import com.labteto.dshmobile.core.session.PlanModeNode
import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.session.RetryNode
import com.labteto.dshmobile.core.session.SubagentNode
import com.labteto.dshmobile.core.session.TitleNode
import com.labteto.dshmobile.core.session.TodoNode
import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.ToolResultNode
import com.labteto.dshmobile.core.session.TurnEndNode
import com.labteto.dshmobile.core.session.TurnErrorNode
import com.labteto.dshmobile.core.session.TurnStartNode
import com.labteto.dshmobile.core.session.UserMessageNode
import com.labteto.dshmobile.core.session.WorkflowNode
import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.dto.ContentBlock
import com.labteto.dshmobile.core.wire.dto.DiffView
import com.labteto.dshmobile.core.wire.dto.GenericView
import com.labteto.dshmobile.core.wire.dto.GoalPhase
import com.labteto.dshmobile.core.wire.dto.GoalSnapshot
import com.labteto.dshmobile.core.wire.dto.ReadView
import com.labteto.dshmobile.core.wire.dto.SearchView
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.core.wire.dto.SkillEntry
import com.labteto.dshmobile.core.wire.dto.SubagentListEntry
import com.labteto.dshmobile.core.wire.dto.TerminalView
import com.labteto.dshmobile.core.wire.dto.ToolEventFor
import com.labteto.dshmobile.core.wire.dto.ToolEventView
import com.labteto.dshmobile.core.wire.dto.ToolView
import com.labteto.dshmobile.core.wire.dto.UnknownView
import com.labteto.dshmobile.core.wire.dto.WebView
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.ui.components.ApprovalPanel
import com.labteto.dshmobile.ui.components.ConnectionBanner
import com.labteto.dshmobile.ui.components.ContentBlockView
import com.labteto.dshmobile.ui.components.DiffHunk
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsMenu
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.MarkdownText
import com.labteto.dshmobile.ui.components.MenuItem
import com.labteto.dshmobile.ui.components.QuestionAnswer
import com.labteto.dshmobile.ui.components.QuestionItem
import com.labteto.dshmobile.ui.components.QuestionOption
import com.labteto.dshmobile.ui.components.QuestionsPanel
import com.labteto.dshmobile.ui.components.ReadLine
import com.labteto.dshmobile.ui.components.SearchFile
import com.labteto.dshmobile.ui.components.SearchMatch
import com.labteto.dshmobile.ui.components.SearchMatches
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.StatsLine
import com.labteto.dshmobile.ui.components.ThinkingRow
import com.labteto.dshmobile.ui.components.ToolCard
import com.labteto.dshmobile.ui.components.ToolCardView
import com.labteto.dshmobile.ui.components.UserBubble
import com.labteto.dshmobile.ui.components.WebCardKind
import com.labteto.dshmobile.ui.components.WebSource
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The chat surface: streamed conversation + dock cards (todo/goal/queue) +
 * composer + models/skills/subagents dialogs.
 *
 * The store is a Hilt [@Singleton][javax.inject.Singleton] (not a
 * [androidx.lifecycle.ViewModel]), so it is resolved through an app-scoped
 * entry point rather than [androidx.hilt.navigation.compose.hiltViewModel].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChatScreenEntryPoint {
    fun sessionStore(): SessionStore
}

@Composable
private fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current.applicationContext
    return remember {
        EntryPointAccessors.fromApplication(context, ChatScreenEntryPoint::class.java).sessionStore()
    }
}

@Composable
fun ChatScreen(
    onOpenDetails: () -> Unit,
    detailsOpen: Boolean,
) {
    val store = rememberSessionStore()
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors

    val conversation by store.currentConversation.collectAsStateWithLifecycle()
    val sessions by store.sessions.collectAsStateWithLifecycle()
    val toolViews by store.toolViews.collectAsStateWithLifecycle()
    val models by store.models.collectAsStateWithLifecycle()
    val skills by store.skills.collectAsStateWithLifecycle()
    val subagents by store.subagents.collectAsStateWithLifecycle()
    val subagentConversation by store.subagentConversation.collectAsStateWithLifecycle()
    val subagentMode by store.subagentMode.collectAsStateWithLifecycle()
    val connectionError by store.connectionError.collectAsStateWithLifecycle()
    val hostInfo by store.hostInfo.collectAsStateWithLifecycle()
    val pendingApproval by store.pendingApproval.collectAsStateWithLifecycle()
    val pendingQuestions by store.pendingQuestions.collectAsStateWithLifecycle()

    val currentSession = sessions.firstOrNull { it.sessionId == conversation?.sessionId }
    val title = currentSession?.title ?: conversation?.sessionId.orEmpty()

    val nodes = conversation?.nodes ?: emptyList()
    val itemCount = nodes.size + if (conversation?.hasMore == true) 1 else 0

    val listState = rememberLazyListState()
    var wasNearBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            total == 0 || last >= total - 2
        }.collect { wasNearBottom = it }
    }
    LaunchedEffect(itemCount) {
        if (wasNearBottom && itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    var draft by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("queue") }
    var showModels by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }
    var showSubagents by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val resolver = context.contentResolver
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty() || bytes.size > 6_000_000) return@launch
            val mediaType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: return@launch
            store.promptWithImage(draft, mode, mediaType, android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT))
            draft = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = DsType.std14Strong,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (skills.isNotEmpty()) {
                    DsPill(
                        text = stringResource(R.string.skills_title),
                        onClick = { showSkills = true },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                if (subagents.isNotEmpty()) {
                    DsPill(
                        text = stringResource(R.string.subagents_title),
                        onClick = { showSubagents = true },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                StateDot(if (conversation?.running == true) StateDotState.Running else StateDotState.Idle)
                if (!detailsOpen) {
                    IconButton(onClick = onOpenDetails) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = stringResource(R.string.chat_details_title),
                            tint = colors.labelTertiary,
                        )
                    }
                }
            }

            connectionError?.let { ConnectionBanner(it) }
            if (conversation?.gap == true) {
                ConnectionBanner(stringResource(R.string.common_reconnecting))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (conversation?.hasMore == true) {
                    item(key = "load-older") {
                        DsButton(
                            text = stringResource(R.string.chat_load_older),
                            onClick = { scope.launch { store.loadOlder() } },
                            variant = DsButtonVariant.Ghost,
                            size = DsButtonSize.Small,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (nodes.isEmpty()) {
                    item(key = "empty") {
                        EmptyHero(
                            headline = stringResource(R.string.chat_empty_title),
                            subtitle = stringResource(R.string.chat_empty_hint),
                        )
                    }
                } else {
                    itemsIndexed(nodes, key = { _, node -> node.seq }) { _, node ->
                        ChatNodeItem(
                            node = node,
                            nodes = nodes,
                            toolViews = toolViews,
                            projections = conversation?.projections ?: emptyMap(),
                            running = conversation?.running == true,
                            onOpenSubagent = { childId ->
                                scope.launch { store.openSubagentTranscript(childId) }
                            },
                        )
                    }
                }
            }

            val conv = conversation
            if (conv != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    parseTodos(conv.projections["todos"])?.let { todos ->
                        TodoDock(todos)
                    }
                    parseGoal(conv.projections["goal"])?.let { goal ->
                        GoalBar(goal, store)
                    }
                    if (conv.queue.isNotEmpty()) {
                        QueueDock(conv.queue, store)
                    }
                }
            }

            // Takeover panels for pending server-initiated requests.
            val approval = pendingApproval
            if (approval != null && approval.sessionId == conversation?.sessionId) {
                ApprovalPanel(
                    toolName = approval.toolName,
                    reason = approval.reason,
                    onAllow = {
                        scope.launch { store.respondApproval(approval.sessionId, approval.approvalId, true) }
                    },
                    onReject = {
                        scope.launch { store.respondApproval(approval.sessionId, approval.approvalId, false) }
                    },
                )
            }
            val questions = pendingQuestions
            if (questions != null && questions.sessionId == conversation?.sessionId) {
                QuestionsPanel(
                    questions = questions.items.map { item ->
                        QuestionItem(
                            id = item.id,
                            question = item.question,
                            detail = item.detail,
                            header = item.header,
                            options = item.options?.map { QuestionOption(it.label, it.description) } ?: emptyList(),
                            multiSelect = item.multiSelect ?: false,
                        )
                    },
                    onSubmit = { answers ->
                        scope.launch {
                            store.answerQuestions(
                                questions.sessionId,
                                answers.map { it.id to it.selected },
                                answers.firstOrNull { it.custom != null }?.custom,
                            )
                        }
                    },
                    onCancel = {
                        scope.launch {
                            // Cancel the batch with empty selections (host treats the batch as answered).
                            store.answerQuestions(
                                questions.sessionId,
                                questions.items.map { it.id to emptyList<String>() },
                                null,
                            )
                        }
                    },
                )
            }

            ComposerSurface(
                draft = draft,
                onDraftChange = { draft = it },
                mode = mode,
                onModeChange = { mode = it },
                models = models,
                running = conversation?.running == true,
                onOpenModels = { showModels = true },
                onAttach = { imagePicker.launch("image/*") },
                onSend = {
                    if (draft.isNotBlank()) {
                        scope.launch { store.prompt(draft, mode) }
                        draft = ""
                    }
                },
                onStop = { scope.launch { store.cancelTurn() } },
            )

            hostInfo?.let {
                Text(
                    stringResource(R.string.connect_harness_version, it.version, it.cwd),
                    style = DsType.caption11,
                    color = colors.labelCaption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
        }
    }

    if (showModels) {
        ModelsDialog(models = models, store = store, onDismiss = { showModels = false })
    }
    if (showSkills) {
        SkillsDialog(
            skills = skills,
            onPick = { name -> draft = "/$name " },
            onDismiss = { showSkills = false },
        )
    }
    if (showSubagents) {
        SubagentsDialog(
            store = store,
            entries = subagents,
            conversation = subagentConversation,
            mode = subagentMode,
            onDismiss = { showSubagents = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Conversation node rendering
// ---------------------------------------------------------------------------

@Composable
private fun ChatNodeItem(
    node: ChatNode,
    nodes: List<ChatNode>,
    toolViews: Map<Long, ToolEventView>,
    projections: Map<String, JsonElement>,
    running: Boolean,
    onOpenSubagent: (String) -> Unit,
) {
    val colors = DsTheme.colors
    when (node) {
        is TurnStartNode -> Unit

        is UserMessageNode -> {
            val text = node.blocks
                .filter { it.kind == "text" }
                .joinToString("\n") { it.text.orEmpty() }
                .ifBlank { node.previewText }
            UserBubble(text)
        }

        is AssistantMessageNode -> {
            val isLast = nodes.lastOrNull()?.seq == node.seq
            val reasoningExpanded = remember(node.seq) { mutableStateMapOf<Int, Boolean>() }
            node.blocks.forEachIndexed { idx, block ->
                when (block.kind) {
                    "text" -> MarkdownText(block.text.orEmpty())
                    "reasoning" -> {
                        val expanded = reasoningExpanded[idx] ?: false
                        ThinkingRow(
                            summary = block.text?.lineSequence()?.firstOrNull()
                                ?: stringResource(R.string.chat_thinking),
                            expanded = expanded,
                            onToggle = { reasoningExpanded[idx] = !expanded },
                            streaming = running && isLast,
                        )
                        if (expanded) MarkdownText(block.text.orEmpty())
                    }
                    "tool-call", "tool-result" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StateDot(
                                if (block.isError) StateDotState.Error else StateDotState.Done,
                                size = 8.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                block.toolName ?: block.kind,
                                style = DsType.caption11.copy(fontFamily = DsType.codeFont),
                                color = colors.labelTertiary,
                            )
                        }
                    }
                    "image" -> {
                        // TODO: render the raster once attachment bytes are fetched
                        // (store.fetchAttachment); caption placeholder for now.
                        Text("🖼", style = DsType.caption11, color = colors.labelTertiary)
                    }
                    else -> block.text?.let {
                        Text(it, style = DsType.caption11, color = colors.labelTertiary)
                    }
                }
            }
            if (node.interrupted) {
                DsPill(text = stringResource(R.string.chat_stopped), warn = true)
            }
            if (node.usage != null) {
                val stats = parseSessionStats(projections)
                if (stats != null) {
                    StatsLine(
                        turns = stats.turns ?: nodes.count { it is TurnStartNode },
                        steps = stats.steps ?: 0,
                        llmMs = stats.llmMs,
                        ttftMs = stats.ttftMs,
                        tokPerSec = stats.tokPerSec,
                    )
                }
            }
        }

        is ToolCallNode -> {
            val result = nodes
                .filterIsInstance<ToolResultNode>()
                .firstOrNull { it.callId == node.callId }
            val card = buildToolCardView(
                call = node,
                result = result,
                callView = toolViews[node.seq],
                resultView = result?.let { toolViews[it.seq] },
                running = result == null && running,
            )
            var expanded by remember(node.callId) { mutableStateOf(false) }
            ToolCard(card, expanded = expanded, onToggle = { expanded = !expanded })
            if (result?.isError == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateDot(StateDotState.Error, size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.common_error),
                        style = DsType.caption11,
                        color = colors.error,
                    )
                }
            }
        }

        // Rendered inside the matching ToolCallNode's card; not a standalone row.
        is ToolResultNode -> Unit

        is TurnEndNode -> when (node.reasonKind) {
            "completed" -> Unit
            "aborted", "interrupted" -> DsPill(text = stringResource(R.string.chat_stopped), warn = true)
            "error" -> Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(StateDotState.Error, size = 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.chat_error_turn) + node.reasonDetail?.let { " · $it" }.orEmpty(),
                    style = DsType.small13,
                    color = colors.error,
                )
            }
            "max-tokens" -> DsPill(text = stringResource(R.string.chat_max_tokens), warn = true)
            else -> Unit
        }

        is TodoNode -> {
            parseTodos(node.todos)?.let { todos -> TodoDock(todos) }
        }

        is GoalNode -> {
            parseGoal(node.data)?.let { goal -> GoalSummary(goal) }
        }

        is PlanModeNode -> {
            DsPill(
                text = stringResource(if (node.active) R.string.plan_mode_on else R.string.plan_mode_off),
                warn = true,
            )
        }

        is CompactionNode -> {
            val summaryText = runCatching {
                val arr = (node.data as? JsonObject)?.get("summary") as? JsonArray
                arr?.mapNotNull { (it as? JsonObject)?.get("text")?.asString() }?.joinToString("\n")
            }.getOrNull()
            var expanded by remember(node.seq) { mutableStateOf(false) }
            DisclosureRow(
                title = stringResource(R.string.chat_compaction),
                summary = stringResource(R.string.chat_compaction_summary),
                expanded = expanded,
                onToggle = { expanded = !expanded },
            ) {
                if (!summaryText.isNullOrBlank()) MarkdownText(summaryText)
            }
        }

        is RetryNode -> {
            val delayMs = runCatching {
                val obj = node.data as? JsonObject
                obj?.get("delayMs")?.asLong()
                    ?: obj?.get("ms")?.asLong()
                    ?: obj?.get("providerRetryAfterMs")?.asLong()
            }.getOrNull()
            val label = if (delayMs != null && delayMs > 0) {
                stringResource(R.string.chat_retry_scheduled, (delayMs / 1000).toInt().coerceAtLeast(1))
            } else {
                stringResource(R.string.common_loading)
            }
            Text(label, style = DsType.caption11, color = colors.labelTertiary)
        }

        is TurnErrorNode -> Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(StateDotState.Error, size = 8.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.chat_error_turn) + " · " + node.message,
                style = DsType.small13,
                color = colors.error,
            )
            node.code?.let {
                Text(" · $it", style = DsType.caption11, color = colors.labelTertiary)
            }
        }

        is CommandNode -> {
            val name = runCatching { (node.data as? JsonObject)?.get("name")?.asString() }.getOrNull()
                ?: node.kind
            var expanded by remember(node.seq) { mutableStateOf(false) }
            DisclosureRow(
                title = name,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            ) {
                Text(
                    node.data.toString(),
                    style = DsType.caption11.copy(fontFamily = DsType.codeFont),
                    color = colors.labelCaption,
                )
            }
        }

        is WorkflowNode -> WorkflowRow(node.data, onOpenMember = onOpenSubagent)

        is TitleNode -> Text(node.title, style = DsType.caption11, color = colors.labelTertiary)
        is SubagentNode -> Text("subagent", style = DsType.caption11, color = colors.labelTertiary)
        is OtherNode -> Text(node.type, style = DsType.caption11, color = colors.labelTertiary)
    }
}

// ---------------------------------------------------------------------------
// Docks
// ---------------------------------------------------------------------------

@Composable
private fun TodoDock(todos: List<TodoEntry>, modifier: Modifier = Modifier) {
    if (todos.isEmpty()) return
    var expanded by remember(todos) { mutableStateOf(false) }
    val completed = todos.count { it.status == "completed" }
    DisclosureRow(
        title = "To-dos", // TODO: no dedicated R.string for the todo dock title.
        summary = "$completed/${todos.size}",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        todos.forEach { todo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateDot(todoStatusDot(todo.status), size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    todo.content,
                    style = DsType.small13,
                    color = DsTheme.colors.labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun GoalSummary(goal: GoalSnapshot) {
    val colors = DsTheme.colors
    SectionHeader(stringResource(R.string.goal_title))
    DsPill(text = stringResource(goalPhaseLabelRes(goal.phase)))
    Spacer(Modifier.height(4.dp))
    Text(goal.objective, style = DsType.small13, color = colors.labelSecondary)
    goal.blockedReason?.let {
        Text(
            stringResource(R.string.goal_blocked_reason, it.message),
            style = DsType.caption11,
            color = colors.warnLabel,
        )
    }
}

@Composable
private fun GoalBar(goal: GoalSnapshot, store: SessionStore, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    var editing by remember { mutableStateOf(false) }
    var editText by remember(goal.revision) { mutableStateOf(goal.objective) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            goal.objective,
            style = DsType.small13,
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DsPill(text = stringResource(goalPhaseLabelRes(goal.phase)))
        Spacer(Modifier.width(4.dp))
        DsMenu(
            anchor = {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.goal_edit),
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            items = buildList {
                when (goal.phase) {
                    GoalPhase.ACTIVE -> add(
                        MenuItem(stringResource(R.string.goal_pause)) {
                            scope.launch { store.goalAction("pause") }
                        },
                    )
                    GoalPhase.PAUSED, GoalPhase.BLOCKED -> add(
                        MenuItem(stringResource(R.string.goal_resume)) {
                            scope.launch { store.goalAction("resume") }
                        },
                    )
                    GoalPhase.COMPLETE -> Unit
                }
                add(MenuItem(stringResource(R.string.goal_edit)) { editing = true })
                add(
                    MenuItem(stringResource(R.string.goal_clear), danger = true) {
                        scope.launch { store.goalAction("clear") }
                    },
                )
            },
        )
    }
    if (editing) {
        DsDialog(title = stringResource(R.string.goal_edit), onDismiss = { editing = false }) {
            TextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.goal_title), style = DsType.std14) },
                colors = dialogTextFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DsButton(
                    text = stringResource(R.string.common_ok),
                    onClick = {
                        scope.launch { store.goalAction("edit", editText) }
                        editing = false
                    },
                    variant = DsButtonVariant.Info,
                )
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { editing = false },
                    variant = DsButtonVariant.Ghost,
                )
            }
        }
    }
}

@Composable
private fun QueueDock(queue: List<QueueItem>, store: SessionStore, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    SectionHeader(
        title = stringResource(R.string.chat_queue_title),
        action = queue.size.toString(),
    )
    queue.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.previewText,
                style = DsType.small13,
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DsPill(text = item.placement)
            DsMenu(
                anchor = {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.chat_queue_edit),
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                items = listOf(
                    MenuItem(stringResource(R.string.chat_queue_edit)) {
                        editingId = item.id
                        editText = item.previewText
                    },
                    MenuItem(stringResource(R.string.chat_queue_remove)) {
                        scope.launch { store.updateQueue(item.id, "remove") }
                    },
                    MenuItem(stringResource(R.string.chat_queue_steer)) {
                        scope.launch { store.updateQueue(item.id, "steer") }
                    },
                ),
            )
        }
    }
    editingId?.let { id ->
        DsDialog(title = stringResource(R.string.chat_queue_edit), onDismiss = { editingId = null }) {
            TextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chat_composer_hint), style = DsType.std14) },
                colors = dialogTextFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DsButton(
                    text = stringResource(R.string.common_ok),
                    onClick = {
                        scope.launch { store.updateQueue(id, "edit", editText) }
                        editingId = null
                    },
                    variant = DsButtonVariant.Info,
                )
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { editingId = null },
                    variant = DsButtonVariant.Ghost,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Workflow
// ---------------------------------------------------------------------------

@Composable
private fun WorkflowRow(data: JsonElement, onOpenMember: (String) -> Unit) {
    val colors = DsTheme.colors
    val obj = data as? JsonObject ?: return
    val name = obj["name"].asString()
    val status = obj["status"].asString() ?: obj["stopReason"].asString() ?: obj["outcome"].asString()
    val membersArr = obj["members"] as? JsonArray ?: obj["phases"] as? JsonArray
    val members: List<WorkflowMember> = when {
        membersArr != null -> membersArr.mapNotNull { m ->
            val mo = m as? JsonObject ?: return@mapNotNull null
            WorkflowMember(
                label = mo["label"].asString() ?: mo["name"].asString(),
                childId = mo["childId"].asString(),
                status = mo["status"].asString() ?: mo["outcome"].asString(),
            )
        }
        else -> {
            val childId = obj["childId"].asString()
            val label = obj["label"].asString()
            if (childId != null || label != null) listOf(WorkflowMember(label, childId, status)) else emptyList()
        }
    }
    var expanded by remember(data) { mutableStateOf(false) }
    DisclosureRow(
        title = stringResource(R.string.workflow_title),
        summary = listOfNotNull(name, workflowStatusLabel(status)).joinToString(" · ").ifEmpty { null },
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        members.forEach { member ->
            val memberChildId = member.childId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 2.dp)
                    .then(
                        if (memberChildId != null) {
                            Modifier.clickable { onOpenMember(memberChildId) }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateDot(workflowMemberDot(member.status), size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    member.label ?: memberChildId.orEmpty(),
                    style = DsType.small13,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                member.status?.let {
                    Text(
                        workflowStatusLabel(it) ?: it,
                        style = DsType.caption11,
                        color = colors.labelTertiary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Composer + dialogs
// ---------------------------------------------------------------------------

@Composable
private fun ComposerSurface(
    draft: String,
    onDraftChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    models: SessionModelsValue?,
    running: Boolean,
    onOpenModels: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = DsTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = DsShapes.composer,
        color = colors.composerCard,
        border = BorderStroke(1.dp, colors.borderL1),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chat_composer_hint), style = DsType.std14, color = colors.labelTertiary) },
                minLines = 1,
                maxLines = 8,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = colors.accent,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DsPill(
                    text = stringResource(R.string.chat_composer_queue),
                    selected = mode == "queue",
                    onClick = { onModeChange("queue") },
                )
                Spacer(Modifier.width(6.dp))
                DsPill(
                    text = stringResource(R.string.chat_composer_steer),
                    selected = mode == "steer",
                    onClick = { onModeChange("steer") },
                )
                Spacer(Modifier.width(6.dp))
                models?.current?.let {
                    DsPill(
                        text = "${it.provider} · ${it.model}",
                        onClick = onOpenModels,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAttach) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = stringResource(R.string.chat_composer_attach),
                        tint = colors.labelTertiary,
                    )
                }
                if (running) {
                    DsButton(
                        text = stringResource(R.string.chat_composer_stop),
                        onClick = onStop,
                        variant = DsButtonVariant.Danger,
                        size = DsButtonSize.Small,
                    )
                } else {
                    DsButton(
                        text = "",
                        icon = Icons.AutoMirrored.Filled.Send,
                        onClick = onSend,
                        variant = DsButtonVariant.Info,
                        enabled = draft.isNotBlank(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelsDialog(
    models: SessionModelsValue?,
    store: SessionStore,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    DsDialog(title = stringResource(R.string.models_title), onDismiss = onDismiss) {
        val m = models
        if (m == null) {
            Text(stringResource(R.string.common_loading), style = DsType.std14, color = colors.labelTertiary)
        } else {
            val current = m.current
            if (!m.routable) {
                Text(stringResource(R.string.models_unroutable), style = DsType.small13, color = colors.warnLabel)
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                m.groups.forEach { group ->
                    SectionHeader(group.name)
                    group.models.forEach { model ->
                        val isCurrent = current.provider == group.id && current.model == model.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { store.selectModel(group.id, model.id) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                model.name,
                                style = DsType.std14,
                                color = if (isCurrent) colors.accent else colors.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.models_current),
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        model.reasoning?.efforts?.let { efforts ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                efforts.forEach { effort ->
                                    DsPill(
                                        text = effort.name,
                                        selected = isCurrent && current.reasoningEffort == effort.id,
                                        onClick = {
                                            scope.launch { store.selectModel(group.id, model.id, effort.id) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsDialog(
    skills: List<SkillEntry>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    DsDialog(title = stringResource(R.string.skills_title), onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            skills.forEach { skill ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPick(skill.name)
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("/${skill.name}", style = DsType.std14Strong, color = colors.labelPrimary)
                        Text(skill.description, style = DsType.caption11, color = colors.labelTertiary)
                    }
                    if (!skill.modelInvocable) {
                        DsPill(text = stringResource(R.string.skills_user_only))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubagentsDialog(
    store: SessionStore,
    entries: List<SubagentListEntry>,
    conversation: ConversationSnapshot?,
    mode: String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    val childId = conversation?.sessionId
    var subDraft by remember { mutableStateOf("") }
    val childRunning = entries
        .firstOrNull { subagentId(it) == childId }
        ?.let { subagentRunning(it) } == true
    DsDialog(title = stringResource(R.string.subagents_title), onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.subagents_empty), style = DsType.caption11, color = colors.labelTertiary)
            }
            entries.forEach { entry ->
                SubagentRow(
                    entry = entry,
                    selected = subagentId(entry) == childId,
                    onClick = {
                        subagentId(entry)?.let { id ->
                            scope.launch { store.openSubagentTranscript(id) }
                        }
                    },
                )
            }

            conversation?.let { conv ->
                if (conv.nodes.isEmpty()) {
                    Text(stringResource(R.string.common_loading), style = DsType.caption11, color = colors.labelTertiary)
                } else {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        conv.nodes.forEach { n -> SubagentTranscriptRow(n) }
                    }
                }

                if (mode != "continuable") {
                    Text(stringResource(R.string.subagents_readonly), style = DsType.caption11, color = colors.labelTertiary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = subDraft,
                            onValueChange = { subDraft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.subagents_message), style = DsType.std14) },
                            colors = dialogTextFieldColors(),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (childRunning) {
                            DsButton(
                                text = stringResource(R.string.subagents_interrupt),
                                onClick = {
                                    childId?.let { id -> scope.launch { store.interruptSubagent(id) } }
                                },
                                variant = DsButtonVariant.Danger,
                                size = DsButtonSize.Small,
                            )
                        } else {
                            DsButton(
                                text = "",
                                icon = Icons.AutoMirrored.Filled.Send,
                                onClick = {
                                    val text = subDraft
                                    val id = childId
                                    if (text.isNotBlank() && id != null) {
                                        scope.launch { store.promptSubagent(id, text) }
                                        subDraft = ""
                                    }
                                },
                                variant = DsButtonVariant.Info,
                                enabled = subDraft.isNotBlank() && childId != null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubagentRow(
    entry: SubagentListEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = DsTheme.colors
    val modeLabel = when (entry) {
        is SubagentListEntry.ChildOneShot -> stringResource(R.string.subagents_oneshot)
        is SubagentListEntry.ChildContinuable -> stringResource(R.string.subagents_continuable)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateDot(if (subagentRunning(entry)) StateDotState.Running else StateDotState.Idle)
        Spacer(Modifier.width(8.dp))
        Text(
            subagentLabel(entry) ?: subagentId(entry).orEmpty(),
            style = DsType.small13,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        modeLabel?.let { DsPill(text = it) }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SubagentTranscriptRow(node: ChatNode) {
    when (node) {
        is UserMessageNode -> UserBubble(node.previewText)
        is AssistantMessageNode -> {
            if (node.plainText.isNotBlank()) MarkdownText(node.plainText)
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Mapping + parsing helpers
// ---------------------------------------------------------------------------

private fun buildToolCardView(
    call: ToolCallNode,
    result: ToolResultNode?,
    callView: ToolEventView?,
    resultView: ToolEventView?,
    running: Boolean,
): ToolCardView {
    resultView
        ?.takeIf { it.for_ == ToolEventFor.RESULT }
        ?.view
        ?.let { return mapToolView(it, running = false) }
    callView
        ?.takeIf { it.for_ == ToolEventFor.CALL }
        ?.view
        ?.let { return mapToolView(it, running = running) }
    return ToolCardView.GenericCard(title = call.name, rawInput = call.arguments)
}

private fun mapToolView(view: ToolView, running: Boolean = false): ToolCardView = when (view) {
    is GenericView -> ToolCardView.GenericCard(
        title = view.title,
        kind = view.kind,
        rawInput = view.rawInput?.toString(),
        locations = view.locations?.map { it.path },
        content = view.content?.map { block ->
            when (block) {
                is ContentBlock.Text -> ContentBlockView.TextBlock(block.text)
                is ContentBlock.Reasoning -> ContentBlockView.ReasoningBlock(block.text)
                is ContentBlock.Image -> ContentBlockView.TextBlock("🖼")
                is ContentBlock.ToolCall -> ContentBlockView.TextBlock("↳ ${block.name}")
                is ContentBlock.ToolResult -> ContentBlockView.TextBlock("↳ result")
                else -> ContentBlockView.TextBlock(block.toString())
            }
        },
    )
    is TerminalView -> ToolCardView.TerminalCard(
        title = view.title,
        description = view.description,
        cwd = view.cwd,
        output = view.output,
        exitCode = view.exitCode,
        signal = view.signal,
        running = view.running ?: running,
    )
    is DiffView -> ToolCardView.DiffCard(
        title = view.title,
        diffs = view.diffs.map { DiffHunk(it.path, it.oldText, it.newText) },
    )
    is SearchView -> ToolCardView.SearchCard(
        title = view.title,
        matches = if (view.shape == "paths") {
            SearchMatches.PathList(view.paths.orEmpty())
        } else {
            SearchMatches.FileMatches(
                view.files.orEmpty().map { file ->
                    SearchFile(
                        path = file.path,
                        matches = file.matches.map { SearchMatch(it.lineNumber, it.line) },
                    )
                },
            )
        },
        truncated = view.truncated,
        total = view.total,
    )
    is ReadView -> ToolCardView.ReadCard(
        label = view.label,
        path = view.path,
        lines = view.lines.map { ReadLine(it.number, it.text) },
        totalLines = view.totalLines,
        lang = view.lang,
    )
    is WebView -> ToolCardView.WebCard(
        title = view.title,
        kind = if (view.kind == "fetch") {
            WebCardKind.Fetch(view.url.orEmpty(), view.statusCode)
        } else {
            WebCardKind.Search(
                answer = view.answer,
                sources = view.sources.orEmpty().map { WebSource(it.url, it.title, it.snippet) },
            )
        },
    )
    is UnknownView -> ToolCardView.GenericCard(title = view.card, rawInput = view.raw.toString())
}

private fun parseGoal(element: JsonElement?): GoalSnapshot? {
    if (element == null) return null
    val direct = runCatching { decodeFromJsonElement(GoalSnapshot.serializer(), element) }.getOrNull()
    if (direct != null) return direct
    val inner = (element as? JsonObject)?.get("goal") ?: return null
    return runCatching { decodeFromJsonElement(GoalSnapshot.serializer(), inner) }.getOrNull()
}

private fun parseTodos(element: JsonElement?): List<TodoEntry>? {
    if (element == null) return null
    return runCatching {
        val obj = element as? JsonObject
        val arr = (obj?.get("todos") ?: element) as? JsonArray ?: return@runCatching emptyList()
        arr.mapNotNull { t ->
            val o = t as? JsonObject ?: return@mapNotNull null
            val content = o["content"].asString() ?: return@mapNotNull null
            TodoEntry(content = content, status = o["status"].asString() ?: "pending")
        }
    }.getOrNull()
}

private fun parseSessionStats(projections: Map<String, JsonElement>): SessionStats? {
    val el = projections["sessionStats"] ?: projections["tokenUsage"] ?: return null
    val obj = el as? JsonObject ?: return null
    return SessionStats(
        turns = obj["turns"].asLong()?.toInt(),
        steps = obj["steps"].asLong()?.toInt(),
        llmMs = obj["llmMs"].asLong(),
        ttftMs = obj["ttftMs"].asLong(),
        tokPerSec = obj["tokPerSec"].asDouble() ?: obj["tokensPerSec"].asDouble(),
    )
}

@Composable
private fun goalPhaseLabelRes(phase: GoalPhase): Int = when (phase) {
    GoalPhase.ACTIVE -> R.string.goal_phase_active
    GoalPhase.PAUSED -> R.string.goal_phase_paused
    GoalPhase.BLOCKED -> R.string.goal_phase_blocked
    GoalPhase.COMPLETE -> R.string.goal_phase_complete
}

private fun todoStatusDot(status: String): StateDotState = when (status) {
    "completed" -> StateDotState.Done
    "in_progress" -> StateDotState.Running
    else -> StateDotState.Idle
}

@Composable
private fun workflowStatusLabel(status: String?): String? = when (status) {
    "running" -> stringResource(R.string.workflow_running)
    "completed" -> stringResource(R.string.workflow_completed)
    "failed", "error", "cancelled" -> stringResource(R.string.workflow_failed)
    else -> null
}

private fun workflowMemberDot(status: String?): StateDotState = when (status) {
    "running" -> StateDotState.Running
    "completed" -> StateDotState.Done
    "failed", "error", "cancelled" -> StateDotState.Error
    else -> StateDotState.Idle
}

private fun subagentId(entry: SubagentListEntry): String? = when (entry) {
    is SubagentListEntry.ChildOneShot -> entry.id
    is SubagentListEntry.ChildContinuable -> entry.id
    is SubagentListEntry.Diagnostic -> entry.id
    else -> null
}

private fun subagentLabel(entry: SubagentListEntry): String? = when (entry) {
    is SubagentListEntry.ChildOneShot -> entry.label
    is SubagentListEntry.ChildContinuable -> entry.label
    is SubagentListEntry.Diagnostic -> entry.reason
    else -> null
}

private fun subagentRunning(entry: SubagentListEntry): Boolean = when (entry) {
    is SubagentListEntry.ChildOneShot -> entry.activity == "running" || entry.activity == "active"
    is SubagentListEntry.ChildContinuable -> entry.activity == "running" || entry.activity == "active"
    else -> false
}

@Composable
private fun dialogTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = DsTheme.colors.bgLayer1,
    unfocusedContainerColor = DsTheme.colors.bgLayer1,
    focusedIndicatorColor = DsTheme.colors.accent,
    unfocusedIndicatorColor = DsTheme.colors.borderL2,
)

// ---------------------------------------------------------------------------
// Defensive JSON accessors + local data shapes
// ---------------------------------------------------------------------------

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()

private data class TodoEntry(val content: String, val status: String)

private data class WorkflowMember(
    val label: String?,
    val childId: String?,
    val status: String?,
)

private data class SessionStats(
    val turns: Int?,
    val steps: Int?,
    val llmMs: Long?,
    val ttftMs: Long?,
    val tokPerSec: Double?,
)
