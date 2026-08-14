package com.labteto.dshmobile.data

import android.util.Base64
import android.util.Log
import com.labteto.dshmobile.connection.ConnectionManager
import com.labteto.dshmobile.connection.ConnectionPhase
import com.labteto.dshmobile.connection.HostsStore
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.core.session.EventFold
import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.session.SessionEventEnvelope
import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.ServerRequest
import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionIntent
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionItem
import com.labteto.dshmobile.core.wire.dto.ContentBlock
import com.labteto.dshmobile.core.wire.dto.GoalClearRequest
import com.labteto.dshmobile.core.wire.dto.GoalCompleteRequest
import com.labteto.dshmobile.core.wire.dto.GoalCreateRequest
import com.labteto.dshmobile.core.wire.dto.GoalEditRequest
import com.labteto.dshmobile.core.wire.dto.GoalPauseRequest
import com.labteto.dshmobile.core.wire.dto.GoalRef
import com.labteto.dshmobile.core.wire.dto.GoalResumeRequest
import com.labteto.dshmobile.core.wire.dto.GoalSnapshot
import com.labteto.dshmobile.core.wire.dto.HostDescription
import com.labteto.dshmobile.core.wire.dto.HostFrame
import com.labteto.dshmobile.core.wire.dto.JobView
import com.labteto.dshmobile.core.wire.dto.MuxFrame
import com.labteto.dshmobile.core.wire.dto.PromptContentPart
import com.labteto.dshmobile.core.wire.dto.QueueAction
import com.labteto.dshmobile.core.wire.dto.SessionAttachmentRequest
import com.labteto.dshmobile.core.wire.dto.SessionCancelRequest
import com.labteto.dshmobile.core.wire.dto.SessionCreateRequest
import com.labteto.dshmobile.core.wire.dto.SessionForkRequest
import com.labteto.dshmobile.core.wire.dto.SessionHistoryRequest
import com.labteto.dshmobile.core.wire.dto.SessionModelsRequest
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.core.wire.dto.SessionPromptRequest
import com.labteto.dshmobile.core.wire.dto.SessionProjectionsBlock
import com.labteto.dshmobile.core.wire.dto.SessionRenameRequest
import com.labteto.dshmobile.core.wire.dto.SessionSelectModelRequest
import com.labteto.dshmobile.core.wire.dto.SessionEvent
import com.labteto.dshmobile.core.wire.dto.SessionUpdateQueueRequest
import com.labteto.dshmobile.core.wire.dto.SkillEntry
import com.labteto.dshmobile.core.wire.dto.SkillListRequest
import com.labteto.dshmobile.core.wire.dto.SubagentHistoryRequest
import com.labteto.dshmobile.core.wire.dto.SubagentInterruptRequest
import com.labteto.dshmobile.core.wire.dto.SubagentListEntry
import com.labteto.dshmobile.core.wire.dto.SubagentListRequest
import com.labteto.dshmobile.core.wire.dto.SubagentPromptRequest
import com.labteto.dshmobile.core.wire.dto.ToolEventView
import com.labteto.dshmobile.core.wire.dto.UnknownHostFrame
import com.labteto.dshmobile.core.wire.dto.UnknownMuxFrame
import com.labteto.dshmobile.core.wire.dto.UnknownSubagentListEntry
import com.labteto.dshmobile.core.wire.dto.WorkspaceArchiveSessionRequest
import com.labteto.dshmobile.core.wire.dto.WorkspaceCreateRequest
import com.labteto.dshmobile.core.wire.dto.WorkspaceDeleteRequest
import com.labteto.dshmobile.core.wire.dto.WorkspaceRenameRequest
import com.labteto.dshmobile.core.wire.dto.WorkspaceView
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One renderable session list row (manual order, live). */
data class SessionRow(
    val sessionId: String,
    val title: String?,
    val running: Boolean,
    val blank: Boolean,
    val parentSessionId: String?,
    val origin: String?,
    val cwd: String?,
    val agentPreset: String?,
    val updatedAt: Long,
    val pendingInteraction: String?, // "approval" | "plan-review" | "question" | null
)

/** One renderable workspace row. */
data class WorkspaceRow(
    val workspaceId: String,
    val path: String,
    val title: String,
    val sessionIds: List<String>,
)

/** A pending sandbox/permission approval the user can answer (allow-once / reject). */
data class PendingApproval(
    val sessionId: String,
    val approvalId: String,
    val rpcId: String,
    val toolName: String,
    val reason: String?,
)

/** A pending ask_user_question batch (a plan review rides the same channel via its intent). */
data class PendingQuestions(
    val sessionId: String,
    val rpcId: String,
    val items: List<AskUserQuestionItem>,
)

/**
 * Single source of truth for the connected harness's live state. All public surface is
 * [StateFlow]; every RPC error becomes [connectionError] and never throws. The store survives
 * reconnects by re-baselining on the connection state transition and on `session/subscribed`.
 */
@Singleton
class SessionStore @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val hostsStore: HostsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val baselineMutex = Mutex()

    // ------------------------------------------------------------------ public StateFlows
    private val _sessions = MutableStateFlow<List<SessionRow>>(emptyList())
    val sessions: StateFlow<List<SessionRow>> = _sessions.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceRow>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceRow>> = _workspaces.asStateFlow()

    private val _archivedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedSessionIds: StateFlow<Set<String>> = _archivedSessionIds.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val searchResults: StateFlow<List<Pair<String, String>>> = _searchResults.asStateFlow()

    private val _currentConversation = MutableStateFlow<ConversationSnapshot?>(null)
    val currentConversation: StateFlow<ConversationSnapshot?> = _currentConversation.asStateFlow()

    private val _jobs = MutableStateFlow<List<JobView>>(emptyList())
    val jobs: StateFlow<List<JobView>> = _jobs.asStateFlow()

    private val _skills = MutableStateFlow<List<SkillEntry>>(emptyList())
    val skills: StateFlow<List<SkillEntry>> = _skills.asStateFlow()

    private val _models = MutableStateFlow<SessionModelsValue?>(null)
    val models: StateFlow<SessionModelsValue?> = _models.asStateFlow()

    private val _hostInfo = MutableStateFlow<HostDescription?>(null)
    val hostInfo: StateFlow<HostDescription?> = _hostInfo.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _toolViews = MutableStateFlow<Map<Long, ToolEventView>>(emptyMap())
    val toolViews: StateFlow<Map<Long, ToolEventView>> = _toolViews.asStateFlow()

    private val _subagents = MutableStateFlow<List<SubagentListEntry>>(emptyList())
    val subagents: StateFlow<List<SubagentListEntry>> = _subagents.asStateFlow()

    private val _subagentConversation = MutableStateFlow<ConversationSnapshot?>(null)
    val subagentConversation: StateFlow<ConversationSnapshot?> = _subagentConversation.asStateFlow()

    private val _subagentMode = MutableStateFlow<String?>(null)
    val subagentMode: StateFlow<String?> = _subagentMode.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingApproval?>(null)
    val pendingApproval: StateFlow<PendingApproval?> = _pendingApproval.asStateFlow()

    private val _pendingQuestions = MutableStateFlow<PendingQuestions?>(null)
    val pendingQuestions: StateFlow<PendingQuestions?> = _pendingQuestions.asStateFlow()

    // ------------------------------------------------------------------ internal state (guarded by `lock`)
    private val sessionRows = LinkedHashMap<String, SessionRow>()
    private val runningBySession = HashMap<String, Boolean>()
    private val titleBySession = HashMap<String, String>()
    private val workspaceRows = LinkedHashMap<String, WorkspaceRow>()
    private val workspaceOrder = ArrayList<String>()
    private var archived = emptySet<String>()
    private val pendingKinds = HashMap<String, MutableSet<String>>()

    // Pending server-initiated requests the store can answer.
    private val approvalRequests = HashMap<String, ApprovalRequest>() // approvalId -> request
    private val questionRpcBySession = HashMap<String, String>() // sessionId -> rpcId

    // Open-session fold state.
    private var currentId: String? = null
    private val currentEvents = ArrayList<SessionEventEnvelope>()
    private var currentHasMore = false
    private var currentBlank = true
    private val currentProjections = HashMap<String, ProjectionValue>()
    private var currentQueue = emptyList<QueueItem>()
    private val toolViewsBySeq = HashMap<Long, ToolEventView>()

    private data class ApprovalRequest(
        val sessionId: String,
        val approvalId: String,
        val rpcId: String,
        val toolName: String,
        val reason: String?,
    )
    private data class ProjectionValue(val seq: Int, val value: JsonElement)

    init {
        observeConnection()
        observeFrames()
    }

    // ------------------------------------------------------------------ connection lifecycle
    private fun observeConnection() {
        scope.launch {
            var prev = connectionManager.state.value
            connectionManager.state.collect { state ->
                val initialConnect = !prev.hasConnected && state.hasConnected
                val reconnect = prev.hasConnected &&
                    prev.phase == ConnectionPhase.RECONNECTING &&
                    state.phase == ConnectionPhase.CONNECTED
                prev = state
                if (initialConnect || reconnect) triggerBaseline()
            }
        }
    }

    private fun observeFrames() {
        scope.launch {
            connectionManager.muxFrames.collect { handleMuxFrame(it) }
        }
        scope.launch {
            connectionManager.hostFrames.collect { handleHostFrame(it) }
        }
    }

    private fun triggerBaseline() {
        scope.launch {
            if (!baselineMutex.tryLock()) return@launch
            try {
                baseline()
            } catch (e: Exception) {
                log("baseline failed", e)
            } finally {
                baselineMutex.unlock()
            }
        }
    }

    private suspend fun baseline() {
        refreshSessions()
        val sid = currentSessionId.value ?: return
        openSession(sid)
    }

    // ------------------------------------------------------------------ frame handlers
    private fun handleMuxFrame(frame: ServerRequest) {
        val mux = parseMuxFrame(frame.payload) ?: return
        when (mux) {
            is MuxFrame.SessionEventFrame -> handleSessionEvent(mux.sessionId, mux.event, mux.view)
            is MuxFrame.SessionSubscribed -> {
                val sid = mux.sessionId
                scope.launch {
                    if (sid == currentSessionId.value) openSession(sid)
                }
            }
            is MuxFrame.ApprovalRequested -> handleApprovalRequested(frame.rpcId, mux)
            is MuxFrame.ApprovalResolved -> handleApprovalResolved(mux)
            is MuxFrame.QuestionRequested -> handleQuestionRequested(frame.rpcId, mux)
            is MuxFrame.QuestionResolved -> handleQuestionResolved(mux)
            is MuxFrame.SessionQueue -> handleSessionQueue(mux)
            is MuxFrame.SessionJobs -> handleSessionJobs(mux)
            is MuxFrame.SessionProjection -> handleSessionProjection(mux)
            is MuxFrame.StreamError -> log("mux stream/error ${mux.error.code}: ${mux.error.message}")
            is UnknownMuxFrame -> log("unknown mux frame ${mux.type}")
        }
    }

    private fun handleHostFrame(frame: ServerRequest) {
        val host = parseHostFrame(frame.payload) ?: return
        when (host) {
            is HostFrame.SessionAdded -> onSessionAdded(host)
            is HostFrame.SessionRemoved -> onSessionRemoved(host.sessionId)
            is HostFrame.SessionStatus -> setRunning(host.sessionId, host.running)
            is HostFrame.AgentError -> setConnectionError(host.message)
            is HostFrame.WorkspaceChanged -> upsertWorkspace(host.workspace)
            is HostFrame.WorkspaceRemoved -> removeWorkspace(host.workspaceId)
            is HostFrame.WorkspaceOrderChanged -> setWorkspaceOrder(host.workspaceIds)
            is HostFrame.ArchivedSessionsChanged -> setArchived(host.archivedSessionIds)
            is HostFrame.RemoteEvent -> Unit // forwarded host cordis event; not surfaced
            is HostFrame.StreamError -> log("host stream/error ${host.error.code}: ${host.error.message}")
            is UnknownHostFrame -> log("unknown host frame ${host.type}")
        }
    }

    private fun handleSessionEvent(sessionId: String, event: SessionEvent, view: ToolEventView?) {
        val envelope = sessionEventToEnvelope(event)
        when (event.type) {
            "turn/start" -> {
                setRunning(sessionId, true)
                setBlank(sessionId, false)
            }
            "turn/end" -> setRunning(sessionId, false)
            "user/message" -> setBlank(sessionId, false)
            "session/title" -> {
                val title = envelope.data.jsonObject["title"]?.jsonPrimitive?.contentOrNull
                if (title != null) setTitle(sessionId, title)
            }
        }
        synchronized(lock) {
            if (sessionId == currentId) {
                appendCurrentEventLocked(envelope)
                if (view != null) {
                    toolViewsBySeq[event.seq.toLong()] = view
                    _toolViews.value = toolViewsBySeq.toMap()
                }
            }
        }
    }

    private fun handleApprovalRequested(rpcId: String, frame: MuxFrame.ApprovalRequested) {
        synchronized(lock) {
            approvalRequests[frame.approvalId] =
                ApprovalRequest(frame.sessionId, frame.approvalId, rpcId, frame.toolName, frame.reason)
            addPendingLocked(frame.sessionId, "approval")
            emitSessionsLocked()
        }
        _pendingApproval.value = PendingApproval(
            sessionId = frame.sessionId,
            approvalId = frame.approvalId,
            rpcId = rpcId,
            toolName = frame.toolName,
            reason = frame.reason,
        )
    }

    private fun handleApprovalResolved(frame: MuxFrame.ApprovalResolved) {
        synchronized(lock) {
            approvalRequests.remove(frame.approvalId)
            removePendingLocked(frame.sessionId, "approval")
            emitSessionsLocked()
        }
        if (_pendingApproval.value?.approvalId == frame.approvalId) _pendingApproval.value = null
    }

    private fun handleQuestionRequested(rpcId: String, frame: MuxFrame.QuestionRequested) {
        synchronized(lock) {
            questionRpcBySession[frame.sessionId] = rpcId
            val kind = if (frame.questions.any { it.intent is AskUserQuestionIntent.PlanReview }) {
                "plan-review"
            } else {
                "question"
            }
            addPendingLocked(frame.sessionId, kind)
            emitSessionsLocked()
        }
        _pendingQuestions.value = PendingQuestions(frame.sessionId, rpcId, frame.questions)
    }

    private fun handleQuestionResolved(frame: MuxFrame.QuestionResolved) {
        synchronized(lock) {
            questionRpcBySession.remove(frame.sessionId)
            removePendingLocked(frame.sessionId, "question")
            removePendingLocked(frame.sessionId, "plan-review")
            emitSessionsLocked()
        }
        if (_pendingQuestions.value?.sessionId == frame.sessionId) _pendingQuestions.value = null
    }

    private fun handleSessionQueue(frame: MuxFrame.SessionQueue) {
        synchronized(lock) {
            if (frame.sessionId == currentId) {
                currentQueue = frame.items.map { queuedInboxItemToQueueItem(it) }
                rebuildCurrentLocked()
            }
        }
    }

    private fun handleSessionJobs(frame: MuxFrame.SessionJobs) {
        synchronized(lock) {
            if (frame.sessionId == currentId) {
                _jobs.value = frame.jobs
            }
        }
    }

    private fun handleSessionProjection(frame: MuxFrame.SessionProjection) {
        synchronized(lock) {
            if (frame.sessionId == currentId) {
                mergeProjectionLocked(frame.key, frame.seq, frame.value)
                rebuildCurrentLocked()
            }
        }
    }

    // ------------------------------------------------------------------ host frame state updates
    private fun onSessionAdded(frame: HostFrame.SessionAdded) {
        synchronized(lock) {
            val existing = sessionRows[frame.sessionId]
            val title = titleBySession[frame.sessionId]
            val row = existing?.copy(
                title = title ?: existing.title,
                blank = frame.blank,
                parentSessionId = frame.parentSessionId,
                origin = frame.origin,
                cwd = frame.cwd,
                agentPreset = frame.agentPreset,
            ) ?: SessionRow(
                sessionId = frame.sessionId,
                title = title,
                running = runningBySession[frame.sessionId] ?: false,
                blank = frame.blank,
                parentSessionId = frame.parentSessionId,
                origin = frame.origin,
                cwd = frame.cwd,
                agentPreset = frame.agentPreset,
                updatedAt = System.currentTimeMillis(),
                pendingInteraction = null,
            )
            if (existing == null) {
                // New sessions appear at the front (most recent first).
                val copy = LinkedHashMap<String, SessionRow>(sessionRows.size + 1)
                copy[frame.sessionId] = row
                copy.putAll(sessionRows)
                sessionRows.clear()
                sessionRows.putAll(copy)
            } else {
                sessionRows[frame.sessionId] = row
            }
            emitSessionsLocked()
        }
    }

    private fun onSessionRemoved(sessionId: String) {
        synchronized(lock) {
            sessionRows.remove(sessionId)
            pendingKinds.remove(sessionId)
            runningBySession.remove(sessionId)
            questionRpcBySession.remove(sessionId)
            emitSessionsLocked()
        }
    }

    private fun setRunning(sessionId: String, running: Boolean) {
        synchronized(lock) {
            runningBySession[sessionId] = running
            sessionRows[sessionId]?.let { if (it.running != running) sessionRows[sessionId] = it.copy(running = running) }
            if (sessionId == currentId) rebuildCurrentLocked()
            emitSessionsLocked()
        }
    }

    private fun setBlank(sessionId: String, blank: Boolean) {
        synchronized(lock) {
            sessionRows[sessionId]?.let { if (it.blank != blank) sessionRows[sessionId] = it.copy(blank = blank) }
            if (sessionId == currentId) currentBlank = blank
            emitSessionsLocked()
        }
    }

    private fun setTitle(sessionId: String, title: String) {
        synchronized(lock) {
            titleBySession[sessionId] = title
            sessionRows[sessionId]?.let { if (it.title != title) sessionRows[sessionId] = it.copy(title = title) }
            emitSessionsLocked()
        }
    }

    private fun upsertWorkspace(workspace: WorkspaceView) {
        synchronized(lock) {
            val row = WorkspaceRow(workspace.workspaceId, workspace.path, workspace.title, workspace.sessionIds)
            if (!workspaceRows.containsKey(workspace.workspaceId)) workspaceOrder.add(workspace.workspaceId)
            workspaceRows[workspace.workspaceId] = row
            emitWorkspacesLocked()
        }
    }

    private fun removeWorkspace(workspaceId: String) {
        synchronized(lock) {
            workspaceRows.remove(workspaceId)
            workspaceOrder.remove(workspaceId)
            emitWorkspacesLocked()
        }
    }

    private fun setWorkspaceOrder(ids: List<String>) {
        synchronized(lock) {
            workspaceOrder.clear()
            workspaceOrder.addAll(ids)
            emitWorkspacesLocked()
        }
    }

    private fun setArchived(ids: List<String>) {
        synchronized(lock) {
            archived = ids.toSet()
            _archivedSessionIds.value = archived
        }
    }

    private fun setConnectionError(message: String?) {
        _connectionError.value = message
    }

    // ------------------------------------------------------------------ open-session fold
    private fun appendCurrentEventLocked(envelope: SessionEventEnvelope) {
        val idx = currentEvents.indexOfFirst { it.seq == envelope.seq }
        if (idx >= 0) {
            currentEvents[idx] = envelope
        } else {
            currentEvents.add(envelope)
            currentEvents.sortBy { it.seq }
        }
        rebuildCurrentLocked()
    }

    private fun mergeProjectionLocked(key: String, seq: Int, value: JsonElement) {
        val existing = currentProjections[key]
        if (existing == null || seq >= existing.seq) {
            currentProjections[key] = ProjectionValue(seq, value)
        }
    }

    private fun rebuildCurrentLocked() {
        val sid = currentId ?: return
        val events = currentEvents.toList()
        val snapshot = EventFold(sid).fold(events)
        val blank = if (events.isEmpty()) currentBlank else snapshot.blank
        val running = runningBySession[sid] ?: snapshot.running
        val merged = snapshot.copy(
            blank = blank,
            running = running,
            hasMore = currentHasMore,
            queue = currentQueue,
            projections = currentProjections.mapValues { it.value.value },
        )
        _currentConversation.value = merged
    }

    private fun emitSessionsLocked() {
        val rows = sessionRows.values.map { row ->
            row.copy(pendingInteraction = pendingInteractionOf(pendingKinds[row.sessionId]))
        }
        _sessions.value = rows
    }

    private fun emitWorkspacesLocked() {
        val ordered = workspaceOrder.mapNotNull { workspaceRows[it] } +
            workspaceRows.values.filter { it.workspaceId !in workspaceOrder }
        _workspaces.value = ordered
    }

    private fun pendingInteractionOf(kinds: Set<String>?): String? {
        if (kinds.isNullOrEmpty()) return null
        return when {
            "question" in kinds -> "question"
            "plan-review" in kinds -> "plan-review"
            "approval" in kinds -> "approval"
            else -> null
        }
    }

    private fun addPendingLocked(sessionId: String, kind: String) {
        pendingKinds.getOrPut(sessionId) { LinkedHashSet() }.add(kind)
    }

    private fun removePendingLocked(sessionId: String, kind: String) {
        pendingKinds[sessionId]?.remove(kind)
        if (pendingKinds[sessionId].isNullOrEmpty()) pendingKinds.remove(sessionId)
    }

    private fun extractTitle(block: SessionProjectionsBlock?): String? {
        val value = block?.values?.get("title") ?: return null
        return when (value) {
            is JsonPrimitive -> value.contentOrNull
            is JsonObject -> value["title"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    // ------------------------------------------------------------------ public RPC surface
    suspend fun refreshSessions() {
        val api = apiOrNull() ?: return
        when (val r = api.sessionList(null)) {
            is RpcResult.Ok -> {
                synchronized(lock) {
                    sessionRows.clear()
                    for (item in r.value.items) {
                        val title = titleBySession[item.sessionId]
                            ?: extractTitle(item.projections)?.also { titleBySession[item.sessionId] = it }
                        runningBySession.putIfAbsent(item.sessionId, item.running)
                        sessionRows[item.sessionId] = SessionRow(
                            sessionId = item.sessionId,
                            title = title,
                            running = runningBySession[item.sessionId] ?: item.running,
                            blank = item.blank,
                            parentSessionId = item.parentSessionId,
                            origin = item.origin,
                            cwd = item.cwd,
                            agentPreset = item.agentPreset,
                            updatedAt = item.updatedAt,
                            pendingInteraction = null,
                        )
                    }
                    emitSessionsLocked()
                }
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
        refreshWorkspaces()
        when (val r = api.hostDescribe()) {
            is RpcResult.Ok -> _hostInfo.value = r.value
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    private suspend fun refreshWorkspaces() {
        val api = apiOrNull() ?: return
        when (val r = api.workspaceList()) {
            is RpcResult.Ok -> {
                synchronized(lock) {
                    workspaceRows.clear()
                    workspaceOrder.clear()
                    for (w in r.value.items) {
                        workspaceRows[w.workspaceId] = WorkspaceRow(w.workspaceId, w.path, w.title, w.sessionIds)
                        workspaceOrder.add(w.workspaceId)
                    }
                    archived = r.value.archivedSessionIds.toSet()
                    _archivedSessionIds.value = archived
                    emitWorkspacesLocked()
                }
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun openSession(sessionId: String) {
        val api = apiOrNull() ?: return
        synchronized(lock) {
            val same = currentId == sessionId
            currentId = sessionId
            _currentSessionId.value = sessionId
            currentEvents.clear()
            currentHasMore = false
            currentBlank = sessionRows[sessionId]?.blank ?: true
            currentProjections.clear()
            currentQueue = emptyList()
            toolViewsBySeq.clear()
            _toolViews.value = emptyMap()
            if (!same) {
                _currentConversation.value = null
                _jobs.value = emptyList()
                _skills.value = emptyList()
                _models.value = null
                _subagents.value = emptyList()
                _subagentConversation.value = null
                _subagentMode.value = null
            }
        }
        when (val r = api.sessionHistory(SessionHistoryRequest(sessionId, null, HISTORY_PAGE_SIZE))) {
            is RpcResult.Ok -> {
                val envelopes = ArrayList<SessionEventEnvelope>(r.value.events.size)
                val views = HashMap<Long, ToolEventView>()
                for (entry in r.value.events) {
                    envelopes.add(sessionEventToEnvelope(entry.event))
                    entry.view?.let { views[entry.event.seq.toLong()] = it }
                }
                synchronized(lock) {
                    if (currentId != sessionId) return@synchronized
                    currentEvents.clear()
                    currentEvents.addAll(envelopes)
                    currentEvents.sortBy { it.seq }
                    currentHasMore = r.value.hasMore
                    toolViewsBySeq.putAll(views)
                    _toolViews.value = toolViewsBySeq.toMap()
                    r.value.projections?.let { block ->
                        block.values.forEach { (key, value) ->
                            currentProjections[key] = ProjectionValue(block.asOfSeq, value)
                        }
                    }
                    rebuildCurrentLocked()
                }
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
        loadSkills(sessionId)
        loadModels(sessionId)
        refreshSubagents()
    }

    suspend fun loadOlder() {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        val oldestSeq = synchronized(lock) { currentEvents.firstOrNull()?.seq }
        when (val r = api.sessionHistory(SessionHistoryRequest(sid, oldestSeq?.toInt(), HISTORY_PAGE_SIZE))) {
            is RpcResult.Ok -> {
                val envelopes = ArrayList<SessionEventEnvelope>(r.value.events.size)
                val views = HashMap<Long, ToolEventView>()
                for (entry in r.value.events) {
                    envelopes.add(sessionEventToEnvelope(entry.event))
                    entry.view?.let { views[entry.event.seq.toLong()] = it }
                }
                synchronized(lock) {
                    if (currentId != sid) return@synchronized
                    val existingSeqs = currentEvents.mapTo(HashSet()) { it.seq }
                    val fresh = envelopes.filter { it.seq !in existingSeqs }
                    if (fresh.isNotEmpty()) {
                        currentEvents.addAll(fresh)
                        currentEvents.sortBy { it.seq }
                    }
                    views.forEach { (seq, view) -> toolViewsBySeq[seq] = view }
                    _toolViews.value = toolViewsBySeq.toMap()
                    currentHasMore = r.value.hasMore
                    rebuildCurrentLocked()
                }
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun createSession(cwd: String? = null, workspaceId: String? = null) {
        val api = apiOrNull() ?: return
        when (val r = api.sessionCreate(SessionCreateRequest(workspaceId = workspaceId, cwd = cwd))) {
            is RpcResult.Ok -> {
                refreshSessions()
                openSession(r.value.sessionId)
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun renameSession(sessionId: String, title: String) {
        val api = apiOrNull() ?: return
        when (val r = api.sessionRename(SessionRenameRequest(sessionId, title))) {
            is RpcResult.Ok -> setTitle(sessionId, r.value.title)
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun forkSession(sessionId: String, atSeq: Long? = null) {
        val api = apiOrNull() ?: return
        when (val r = api.sessionFork(SessionForkRequest(sessionId, atSeq?.toInt()))) {
            is RpcResult.Ok -> refreshSessions()
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun archiveSession(sessionId: String) {
        val api = apiOrNull() ?: return
        when (val r = api.workspaceArchiveSession(WorkspaceArchiveSessionRequest(sessionId))) {
            is RpcResult.Ok -> {
                setArchived(r.value.archivedSessionIds)
                refreshSessions()
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun prompt(text: String, mode: String) =
        promptContent(mode, listOf(PromptContentPart.Text(text)))

    /** Prompt with an attached raster image (bytes submitted base64, as the browser wire does). */
    suspend fun promptWithImage(
        text: String,
        mode: String,
        mediaType: String,
        base64Data: String,
        name: String? = null,
    ) {
        val parts = mutableListOf<PromptContentPart>()
        if (text.isNotBlank()) parts.add(PromptContentPart.Text(text))
        parts.add(PromptContentPart.Image(mediaType, base64Data, name))
        promptContent(mode, parts)
    }

    private suspend fun promptContent(mode: String, content: List<PromptContentPart>) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        val safeMode = if (mode == "steer") "steer" else "queue"
        val zone = TimeZone.getDefault().id
        val request = SessionPromptRequest(
            sessionId = sid,
            mode = safeMode,
            content = content,
            clientTimeZone = zone,
        )
        when (val r = api.sessionPrompt(request)) {
            is RpcResult.Ok -> Unit
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun cancelTurn() {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        when (val r = api.sessionCancel(SessionCancelRequest(sid))) {
            is RpcResult.Ok -> Unit
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun updateQueue(itemId: String, action: String, contentText: String? = null) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        val queueAction: QueueAction = when (action) {
            "remove" -> QueueAction.Remove()
            "steer" -> QueueAction.Steer()
            else -> QueueAction.Edit(listOf(ContentBlock.Text(contentText.orEmpty())))
        }
        when (val r = api.sessionUpdateQueue(SessionUpdateQueueRequest(sid, itemId, queueAction))) {
            is RpcResult.Ok -> Unit
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun respondApproval(sessionId: String, approvalId: String, allow: Boolean) {
        val api = apiOrNull() ?: return
        val request = synchronized(lock) { approvalRequests[approvalId] }
        if (request == null) {
            log("no pending approval for id $approvalId")
            return
        }
        val outcome = if (allow) "allowed-once" else "rejected"
        val value = buildJsonObject {
            put("sessionId", JsonPrimitive(sessionId))
            put("approvalId", JsonPrimitive(approvalId))
            put("outcome", JsonPrimitive(outcome))
        }
        val receipt = api.respond(request.rpcId, value)
        if (receipt == null) log("approval response not acknowledged for $approvalId")
    }

    suspend fun answerQuestions(
        sessionId: String,
        answers: List<Pair<String, List<String>>>,
        custom: String? = null,
    ) {
        val api = apiOrNull() ?: return
        val rpcId = synchronized(lock) { questionRpcBySession[sessionId] }
        if (rpcId == null) {
            log("no pending question for session $sessionId")
            return
        }
        val answersJson = JsonArray(answers.map { (id, selected) ->
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("selected", JsonArray(selected.map { JsonPrimitive(it) }))
            }
        })
        val answerObj = buildJsonObject {
            put("answers", answersJson)
            custom?.let { put("custom", JsonPrimitive(it)) }
        }
        val value = buildJsonObject {
            put("sessionId", JsonPrimitive(sessionId))
            put("answer", answerObj)
        }
        val receipt = api.respond(rpcId, value)
        if (receipt == null) log("question response not acknowledged for $sessionId")
    }

    suspend fun selectModel(provider: String, model: String, reasoningEffort: String? = null) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        val request = SessionSelectModelRequest(sid, provider, model, reasoningEffort)
        when (val r = api.sessionSelectModel(request)) {
            is RpcResult.Ok -> loadModels(sid)
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun search(query: String) {
        val api = apiOrNull() ?: return
        when (val r = api.sessionSearch(query)) {
            is RpcResult.Ok -> _searchResults.value = r.value.items.map { it.sessionId to it.snippet }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun fetchAttachment(attachmentId: String): ByteArray? {
        val sid = currentSessionId.value ?: return null
        val api = apiOrNull() ?: return null
        return when (val r = api.sessionAttachment(SessionAttachmentRequest(sid, attachmentId))) {
            is RpcResult.Ok -> runCatching { Base64.decode(r.value.data, Base64.DEFAULT) }.getOrNull()
            is RpcResult.Err -> {
                setConnectionError(r.error.message)
                null
            }
        }
    }

    suspend fun listSkills() {
        val sid = currentSessionId.value ?: return
        loadSkills(sid)
    }

    suspend fun refreshSubagents() {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        when (val r = api.subagentList(SubagentListRequest(sid))) {
            is RpcResult.Ok -> synchronized(lock) {
                if (currentId == sid) _subagents.value = r.value.entries
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun interruptSubagent(childSessionId: String) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        when (val r = api.subagentInterrupt(SubagentInterruptRequest(parentSessionId = sid, childSessionId = childSessionId))) {
            is RpcResult.Ok -> Unit
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun promptSubagent(childSessionId: String, text: String) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        val zone = TimeZone.getDefault().id
        val request = SubagentPromptRequest(
            parentSessionId = sid,
            childSessionId = childSessionId,
            mode = "continuable",
            content = listOf(ContentBlock.Text(text)),
            clientTimeZone = zone,
        )
        when (val r = api.subagentPrompt(request)) {
            is RpcResult.Ok -> Unit
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun openSubagentTranscript(childSessionId: String) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        val entry = _subagents.value.firstOrNull { subagentEntryId(it) == childSessionId }
        val mode = when (entry) {
            is SubagentListEntry.ChildOneShot -> "one-shot"
            is SubagentListEntry.ChildContinuable -> "continuable"
            else -> null
        }
        _subagentMode.value = mode
        if (mode == null) {
            _subagentConversation.value = null
            log("subagent $childSessionId has no readable transcript mode")
            return
        }
        val request = SubagentHistoryRequest(sid, childSessionId, mode, null, HISTORY_PAGE_SIZE)
        when (val r = api.subagentHistory(request)) {
            is RpcResult.Ok -> {
                val envelopes = r.value.events.mapNotNull { sessionEventToEnvelope(it.event) }
                val snapshot = EventFold(childSessionId).fold(envelopes).copy(
                    hasMore = r.value.hasMore,
                    projections = r.value.projections?.values ?: emptyMap(),
                )
                _subagentConversation.value = snapshot
            }
            is RpcResult.Err -> {
                _subagentConversation.value = null
                setConnectionError(r.error.message)
            }
        }
    }

    suspend fun createWorkspace(path: String) {
        val api = apiOrNull() ?: return
        when (val r = api.workspaceCreate(WorkspaceCreateRequest(path))) {
            is RpcResult.Ok -> refreshWorkspaces()
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun renameWorkspace(id: String, title: String) {
        val api = apiOrNull() ?: return
        when (val r = api.workspaceRename(WorkspaceRenameRequest(id, title))) {
            is RpcResult.Ok -> refreshWorkspaces()
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun deleteWorkspace(id: String) {
        val api = apiOrNull() ?: return
        when (val r = api.workspaceDelete(WorkspaceDeleteRequest(id))) {
            is RpcResult.Ok -> refreshWorkspaces()
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    suspend fun goalAction(action: String, objective: String? = null) {
        val sid = currentSessionId.value ?: return
        val api = apiOrNull() ?: return
        when (action) {
            "create" -> {
                val obj = objective
                if (obj.isNullOrBlank()) {
                    log("goal create requires an objective")
                    return
                }
                handleResult(api.goalCreate(GoalCreateRequest(sid, obj)))
            }
            "edit", "pause", "resume", "complete", "clear" -> {
                val ref = synchronized(lock) { goalRefFromProjectionLocked() }
                if (ref == null) {
                    log("goal $action requires a current goal (no goal projection)")
                    return
                }
                when (action) {
                    "edit" -> handleResult(api.goalEdit(GoalEditRequest(sid, ref, objective)))
                    "pause" -> handleResult(api.goalPause(GoalPauseRequest(sid, ref)))
                    "resume" -> handleResult(api.goalResume(GoalResumeRequest(sid, ref)))
                    "complete" -> handleResult(api.goalComplete(GoalCompleteRequest(sid, ref)))
                    "clear" -> handleResult(api.goalClear(GoalClearRequest(sid, ref)))
                }
            }
            else -> log("unknown goal action $action")
        }
    }

    suspend fun exportSessionUrl(): String? {
        val sid = currentSessionId.value ?: return null
        val host = connectionManager.state.value.host ?: return null
        return "${host.baseUrl}/api/session.export?sessionId=$sid"
    }

    /** True while [sessionId] is the session currently open in the foreground. */
    fun isSessionOpen(sessionId: String): Boolean = currentSessionId.value == sessionId

    // ------------------------------------------------------------------ internal helpers
    private fun goalRefFromProjectionLocked(): GoalRef? {
        val value = currentProjections["goal"]?.value ?: return null
        return runCatching {
            val snapshot = decodeFromJsonElement(GoalSnapshot.serializer(), value)
            GoalRef(snapshot.id, snapshot.revision)
        }.getOrElse {
            runCatching { decodeFromJsonElement(GoalRef.serializer(), value) }.getOrNull()
        }
    }

    private suspend fun loadSkills(sessionId: String) {
        val api = apiOrNull() ?: return
        when (val r = api.skillList(SkillListRequest(sessionId))) {
            is RpcResult.Ok -> synchronized(lock) {
                if (currentId == sessionId) _skills.value = r.value.skills
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    private suspend fun loadModels(sessionId: String) {
        val api = apiOrNull() ?: return
        when (val r = api.sessionModels(SessionModelsRequest(sessionId))) {
            is RpcResult.Ok -> synchronized(lock) {
                if (currentId == sessionId) _models.value = r.value
            }
            is RpcResult.Err -> setConnectionError(r.error.message)
        }
    }

    private fun subagentEntryId(entry: SubagentListEntry): String? = when (entry) {
        is SubagentListEntry.ChildOneShot -> entry.id
        is SubagentListEntry.ChildContinuable -> entry.id
        is SubagentListEntry.Diagnostic -> entry.id
        is UnknownSubagentListEntry -> null
    }

    private fun apiOrNull(): DshApiClient? {
        val api = connectionManager.connectedApi
        if (api == null) log("not connected — ignoring request")
        return api
    }

    private inline fun <T> handleResult(result: RpcResult<T>) {
        when (result) {
            is RpcResult.Ok -> Unit
            is RpcResult.Err -> setConnectionError(result.error.message)
        }
    }

    private fun log(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    private companion object {
        const val TAG = "SessionStore"
        const val HISTORY_PAGE_SIZE = 60
    }
}
