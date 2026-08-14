package com.labteto.dshmobile.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.core.session.PlanModeNode
import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.ToolResultNode
import com.labteto.dshmobile.core.session.TurnStartNode
import com.labteto.dshmobile.core.session.UserMessageNode
import com.labteto.dshmobile.core.session.WorkflowNode
import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.dto.GoalPhase
import com.labteto.dshmobile.core.wire.dto.GoalSnapshot
import com.labteto.dshmobile.core.wire.dto.HostDescription
import com.labteto.dshmobile.core.wire.dto.JobStatus
import com.labteto.dshmobile.core.wire.dto.JobView
import com.labteto.dshmobile.core.wire.dto.SubagentListEntry
import com.labteto.dshmobile.core.wire.dto.UnknownSubagentListEntry
import com.labteto.dshmobile.data.SessionRow
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsToastHost
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.rememberDsToast
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val MonoXsmall = DsType.xsmall12.copy(fontFamily = DsType.codeFont)

/** Hilt entry point used to resolve the process-scoped [SessionStore] singleton. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DetailsPanelEntryPoint {
    fun sessionStore(): SessionStore
}

/** Resolves the process-scoped [SessionStore] once per composition. */
@Composable
private fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current.applicationContext
    return remember {
        EntryPointAccessors.fromApplication(context, DetailsPanelEntryPoint::class.java).sessionStore()
    }
}

/**
 * Right-hand session details panel (host / session / goal / plan / jobs /
 * queue / subagents / trajectory / workflow overview) — the Discord
 * "members panel" analog. Reads live state straight from [SessionStore].
 */
@Composable
fun DetailsPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val store: SessionStore = rememberSessionStore()
    val colors = DsTheme.colors
    val currentConversation by store.currentConversation.collectAsState()
    val jobs by store.jobs.collectAsState()
    val hostInfo by store.hostInfo.collectAsState()
    val subagents by store.subagents.collectAsState()
    val sessions by store.sessions.collectAsState()
    val currentSessionId by store.currentSessionId.collectAsState()
    val scope = rememberCoroutineScope()
    val toast = rememberDsToast()
    val clipboard = LocalClipboardManager.current
    val copyLabel = stringResource(R.string.common_copy)
    val copiedLabel = stringResource(R.string.common_copied)

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = colors.bgLayer1,
        shadowElevation = 8.dp,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeaderRow(onClose)
                HostSection(hostInfo)
                SessionSection(sessions, currentSessionId)

                val conversation = currentConversation
                if (conversation == null) {
                    Text(
                        stringResource(R.string.chat_details_empty),
                        style = DsType.caption11,
                        color = colors.labelTertiary,
                    )
                } else {
                    GoalSection(conversation)
                    PlanSection(
                        conversation = conversation,
                        onTogglePlan = { scope.launch { store.prompt("/plan", "queue") } },
                    )
                    JobsSection(jobs)
                    QueueSection(conversation.queue)
                    SubagentsSection(subagents)
                    TrajectorySection(conversation.nodes)
                    WorkflowSection(conversation.nodes)
                    DsButton(
                        text = copyLabel,
                        onClick = {
                            scope.launch {
                                store.exportSessionUrl()?.let { url ->
                                    clipboard.setText(AnnotatedString(url))
                                    toast.second(copiedLabel)
                                }
                            }
                        },
                        variant = DsButtonVariant.Ghost,
                        size = DsButtonSize.Small,
                    )
                }
            }
            DsToastHost(toast, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeaderRow(onClose: () -> Unit) {
    val colors = DsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = colors.labelSecondary,
            )
        }
        Text(
            stringResource(R.string.chat_details_title),
            style = DsType.std14Strong,
            color = colors.labelPrimary,
        )
    }
}

@Composable
private fun HostSection(hostInfo: HostDescription?) {
    if (hostInfo == null) return
    val colors = DsTheme.colors
    SectionHeader(stringResource(R.string.settings_host_info))
    Text(
        stringResource(R.string.connect_harness_version, hostInfo.version, hostInfo.cwd),
        style = DsType.caption11,
        color = colors.labelCaption,
    )
    Text(
        "${hostInfo.attachedSessions} attached",
        style = DsType.caption11,
        color = colors.labelCaption,
    )
}

@Composable
private fun SessionSection(sessions: List<SessionRow>, currentSessionId: String?) {
    val colors = DsTheme.colors
    val current = sessions.firstOrNull { it.sessionId == currentSessionId } ?: return
    SectionHeader(current.title ?: current.sessionId)
    current.cwd?.let { cwd ->
        Text(cwd, style = DsType.caption11, color = colors.labelCaption)
    }
    if (current.running) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(StateDotState.Running)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.jobs_running),
                style = DsType.caption11,
                color = colors.labelSecondary,
            )
        }
    }
}

@Composable
private fun GoalSection(conversation: ConversationSnapshot) {
    val colors = DsTheme.colors
    val goal = parseGoal(conversation.projections["goal"])
    SectionHeader(stringResource(R.string.goal_title))
    if (goal == null) {
        Text(
            stringResource(R.string.goal_none),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            goal.objective,
            style = DsType.small13,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DsPill(text = stringResource(goalPhaseLabelRes(goal.phase)))
    }
    goal.blockedReason?.let { reason ->
        Text(
            stringResource(R.string.goal_blocked_reason, reason.message),
            style = DsType.caption11,
            color = colors.warnLabel,
        )
    }
    if (goal.maxGoalRounds > 0) {
        Text(
            stringResource(R.string.goal_max_rounds, goal.maxGoalRounds.toString()),
            style = DsType.caption11,
            color = colors.labelCaption,
        )
    }
}

@Composable
private fun PlanSection(conversation: ConversationSnapshot, onTogglePlan: () -> Unit) {
    val colors = DsTheme.colors
    val active = parsePlanActive(conversation.projections, conversation.nodes) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (active) R.string.plan_mode_on else R.string.plan_mode_off),
            style = DsType.caption11,
            color = colors.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        DsPill(
            text = stringResource(if (active) R.string.plan_mode_off else R.string.plan_mode_on),
            onClick = onTogglePlan,
        )
    }
}

@Composable
private fun JobsSection(jobs: List<JobView>) {
    val colors = DsTheme.colors
    SectionHeader(
        title = stringResource(R.string.jobs_title),
        action = if (jobs.isNotEmpty()) jobs.size.toString() else null,
    )
    if (jobs.isEmpty()) {
        Text(
            stringResource(R.string.jobs_empty),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
        return
    }
    jobs.forEach { job -> JobRow(job) }
}

@Composable
private fun JobRow(job: JobView) {
    val colors = DsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(jobStatusDot(job.status))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                job.label,
                style = DsType.small13,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                job.detail?.let { "${job.kind} · $it" } ?: job.kind,
                style = DsType.caption11,
                color = colors.labelCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(job),
            style = DsType.caption11,
            color = colors.labelCaption,
        )
    }
}

@Composable
private fun QueueSection(queue: List<QueueItem>) {
    val colors = DsTheme.colors
    SectionHeader(stringResource(R.string.chat_queue_title))
    if (queue.isEmpty()) {
        Text(
            stringResource(R.string.chat_queue_empty),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
        return
    }
    queue.forEach { item ->
        Column {
            Text(
                item.previewText,
                style = DsType.small13,
                color = colors.labelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.placement, style = DsType.caption11, color = colors.labelCaption)
        }
    }
}

@Composable
private fun SubagentsSection(subagents: List<SubagentListEntry>) {
    val colors = DsTheme.colors
    SectionHeader(stringResource(R.string.subagents_title))
    if (subagents.isEmpty()) {
        Text(
            stringResource(R.string.subagents_empty),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
        return
    }
    subagents.forEach { entry -> SubagentRow(entry) }
}

@Composable
private fun SubagentRow(entry: SubagentListEntry) {
    val colors = DsTheme.colors
    val id = when (entry) {
        is SubagentListEntry.ChildOneShot -> entry.id
        is SubagentListEntry.ChildContinuable -> entry.id
        is SubagentListEntry.Diagnostic -> entry.id
        is UnknownSubagentListEntry -> null
    }
    val label = when (entry) {
        is SubagentListEntry.ChildOneShot -> entry.label
        is SubagentListEntry.ChildContinuable -> entry.label
        else -> null
    }
    val activity = when (entry) {
        is SubagentListEntry.ChildOneShot -> entry.activity
        is SubagentListEntry.ChildContinuable -> entry.activity
        else -> null
    }
    val running = activity == "running" || activity == "active"
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(if (running) StateDotState.Running else StateDotState.Idle)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label ?: id ?: "",
                style = DsType.small13,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (running) R.string.subagents_running else R.string.subagents_inactive),
                style = DsType.caption11,
                color = colors.labelCaption,
            )
        }
    }
}

@Composable
private fun TrajectorySection(nodes: List<ChatNode>) {
    val colors = DsTheme.colors
    SectionHeader(
        title = stringResource(R.string.trajectory_title),
        action = stringResource(R.string.trajectory_ledger),
    )
    val groups = groupByTurn(nodes)
    if (groups.isEmpty()) {
        Text(
            stringResource(R.string.trajectory_empty),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
        return
    }
    groups.forEach { (turn, groupNodes) ->
        SectionHeader("Turn $turn")
        groupNodes.forEach { node ->
            when (node) {
                is UserMessageNode -> {
                    val preview = node.previewText.trim()
                    if (preview.isNotEmpty()) {
                        Text(
                            "> $preview",
                            style = DsType.caption11,
                            color = colors.labelSecondary,
                        )
                    }
                }
                is AssistantMessageNode -> {
                    val snippet = node.plainText.trim().take(120)
                    if (snippet.isNotEmpty()) {
                        Text(
                            snippet,
                            style = DsType.caption11,
                            color = colors.labelTertiary,
                        )
                    }
                }
                is ToolCallNode -> {
                    val result = groupNodes
                        .filterIsInstance<ToolResultNode>()
                        .firstOrNull { it.callId == node.callId }
                    ToolTrajectoryRow(node, result)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ToolTrajectoryRow(call: ToolCallNode, result: ToolResultNode?) {
    val colors = DsTheme.colors
    var expanded by remember(call.callId) { mutableStateOf(false) }
    DisclosureRow(
        title = call.name,
        summary = call.arguments.take(60),
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        Text(
            call.arguments,
            style = MonoXsmall,
            color = colors.labelTertiary,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp),
        )
        result?.content?.let { content ->
            Text(
                content.toString(),
                style = MonoXsmall,
                color = if (result.isError) colors.error else colors.labelTertiary,
                modifier = Modifier.padding(start = 28.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun WorkflowSection(nodes: List<ChatNode>) {
    val workflows = parseWorkflows(nodes)
    if (workflows.isEmpty()) return
    SectionHeader(stringResource(R.string.workflow_title))
    workflows.forEach { workflow -> WorkflowRow(workflow) }
}

@Composable
private fun WorkflowRow(workflow: WorkflowView) {
    val colors = DsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                workflow.name,
                style = DsType.small13,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.workflow_members, workflow.members),
                style = DsType.caption11,
                color = colors.labelCaption,
            )
        }
        Spacer(Modifier.width(8.dp))
        DsPill(
            text = stringResource(
                when (workflow.status) {
                    WorkflowStatus.Running -> R.string.workflow_running
                    WorkflowStatus.Completed -> R.string.workflow_completed
                    WorkflowStatus.Failed -> R.string.workflow_failed
                },
            ),
            warn = workflow.status == WorkflowStatus.Failed,
        )
    }
}

// ------------------------------------------------------------------ parsing helpers

private fun parseGoal(element: JsonElement?): GoalSnapshot? {
    if (element == null) return null
    val direct = runCatching {
        decodeFromJsonElement(GoalSnapshot.serializer(), element)
    }.getOrNull()
    if (direct != null) return direct
    val inner = (element as? JsonObject)?.get("goal") ?: return null
    return runCatching {
        decodeFromJsonElement(GoalSnapshot.serializer(), inner)
    }.getOrNull()
}

private fun parsePlanActive(
    projections: Map<String, JsonElement>,
    nodes: List<ChatNode>,
): Boolean? {
    val fromProjection = projections["plan"]?.let { element ->
        runCatching {
            val obj = element as? JsonObject ?: return@runCatching null
            obj["active"]?.jsonPrimitive?.booleanOrNull
                ?: obj["active"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: (obj["plan"] as? JsonObject)?.get("active")?.jsonPrimitive?.booleanOrNull
        }.getOrNull()
    }
    return fromProjection ?: nodes.filterIsInstance<PlanModeNode>().lastOrNull()?.active
}

private fun groupByTurn(nodes: List<ChatNode>): List<Pair<Int, List<ChatNode>>> {
    val groups = mutableListOf<Pair<Int, MutableList<ChatNode>>>()
    for (node in nodes) {
        when {
            node is TurnStartNode -> groups.add(node.turn to mutableListOf())
            groups.isNotEmpty() -> groups.last().second.add(node)
        }
    }
    return groups.map { (turn, list) -> turn to list.toList() }
}

private enum class WorkflowStatus { Running, Completed, Failed }

private data class WorkflowView(
    val runId: String,
    val name: String,
    val status: WorkflowStatus,
    val members: Int,
)

private class WorkflowBuilder(val runId: String) {
    var name: String = ""
    val memberSeqs = mutableSetOf<String>()
    var status: WorkflowStatus? = null
    var failed = false
}

private fun parseWorkflows(nodes: List<ChatNode>): List<WorkflowView> {
    val builders = linkedMapOf<String, WorkflowBuilder>()
    for (node in nodes) {
        if (node !is WorkflowNode) continue
        val data = node.data as? JsonObject ?: continue
        val runId = data["runId"]?.jsonPrimitive?.contentOrNull ?: continue
        val builder = builders.getOrPut(runId) { WorkflowBuilder(runId) }
        when (node.kind) {
            "tool-workflow/run-start" ->
                data["name"]?.jsonPrimitive?.contentOrNull?.let { builder.name = it }

            "tool-workflow/agent-start" ->
                data["seq"]?.jsonPrimitive?.contentOrNull?.let { builder.memberSeqs.add(it) }

            "tool-workflow/agent-end" -> {
                val outcome = data["outcome"]?.jsonPrimitive?.contentOrNull
                if (outcome == "failed" || outcome == "cancelled") builder.failed = true
            }

            "tool-workflow/run-end" -> {
                val stop = data["stopReason"]?.jsonPrimitive?.contentOrNull
                builder.status = if (stop == "completed") WorkflowStatus.Completed else WorkflowStatus.Failed
            }
        }
    }
    return builders.values.map { builder ->
        WorkflowView(
            runId = builder.runId,
            name = builder.name.ifBlank { builder.runId },
            status = builder.status ?: if (builder.failed) WorkflowStatus.Failed else WorkflowStatus.Running,
            members = builder.memberSeqs.size,
        )
    }
}

private fun goalPhaseLabelRes(phase: GoalPhase): Int = when (phase) {
    GoalPhase.ACTIVE -> R.string.goal_phase_active
    GoalPhase.PAUSED -> R.string.goal_phase_paused
    GoalPhase.BLOCKED -> R.string.goal_phase_blocked
    GoalPhase.COMPLETE -> R.string.goal_phase_complete
}

private fun jobStatusDot(status: JobStatus): StateDotState = when (status) {
    JobStatus.RUNNING, JobStatus.STOPPING -> StateDotState.Running
    JobStatus.COMPLETED -> StateDotState.Done
    JobStatus.KILLED, JobStatus.FAILED -> StateDotState.Error
}

private fun formatDuration(job: JobView): String {
    val end = job.finishedAt ?: System.currentTimeMillis()
    val totalSeconds = ((end - job.startedAt) / 1000L).coerceAtLeast(0L)
    val minutes = (totalSeconds / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    return "%d:%02d".format(minutes, seconds)
}
