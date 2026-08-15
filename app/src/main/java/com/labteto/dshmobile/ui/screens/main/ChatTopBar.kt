package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.skeleton
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/** The two views of a session the harness offers. */
internal enum class ChatTab { Chat, Trajectory }

/**
 * The session chrome: a two-row bar plus the Chat / Trajectory tabs.
 *
 * Row one carries the controls that belong to the *connection* — the drawer, the model, the live
 * status. Row two carries the ones that belong to the *session* — its title, its agent preset, its
 * subagents. Splitting them is what makes room for the model selector on the left without eliding
 * the session title down to nothing on a phone.
 */
@Composable
internal fun ChatTopBar(
    title: String,
    running: Boolean,
    models: SessionModelsValue?,
    agentPresetLabel: String?,
    subagentCount: Int,
    detailsOpen: Boolean,
    tab: ChatTab,
    onOpenDrawer: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenSubagents: () -> Unit,
    onOpenDetails: () -> Unit,
    onTabChange: (ChatTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    Column(modifier.fillMaxWidth().background(colors.bgBase)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = DsSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DsIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.chatlist_open),
                onClick = onOpenDrawer,
                tint = colors.labelSecondary,
            )
            ModelChip(models = models, onClick = onOpenModels)
            Spacer(Modifier.weight(1f))
            StateDot(if (running) StateDotState.Running else StateDotState.Idle)
            if (!detailsOpen) {
                DsIconButton(
                    icon = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.chat_details_title),
                    onClick = onOpenDetails,
                    tint = colors.labelTertiary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.medium),
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
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.small, vertical = DsSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
        ) {
            if (agentPresetLabel != null) {
                MetaChip(
                    icon = Icons.Outlined.Dashboard,
                    label = agentPresetLabel,
                    onClick = onOpenPresets,
                )
            }
            if (subagentCount > 0) {
                MetaChip(
                    icon = Icons.Outlined.Groups,
                    label = "$subagentCount",
                    onClick = onOpenSubagents,
                )
            }
        }

        ChatTabRow(tab = tab, onTabChange = onTabChange)
    }
}

/**
 * The model chip: display names, not wire ids.
 *
 * `session.models` returns `deepseek-official / deepseek-v4-pro / max`, which is not what anyone
 * calls it — the catalog's own names resolve that to `DeepSeek-V4-Pro Max`.
 */
@Composable
private fun ModelChip(models: SessionModelsValue?, onClick: () -> Unit) {
    val colors = DsTheme.colors
    if (models == null) {
        Box(
            Modifier
                .padding(horizontal = DsSpacing.small)
                .width(120.dp)
                .height(14.dp)
                .skeleton(colors.bgLayer2, colors.hover),
        )
        return
    }
    val current = models.current
    val group = models.groups.firstOrNull { it.id == current.provider }
    val model = group?.models?.firstOrNull { it.id == current.model }
    val effort = model?.reasoning?.efforts?.firstOrNull { it.id == current.reasoningEffort }
    val modelLabel = model?.name ?: current.model

    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(DsShapes.cube)
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.small, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        if (!models.routable) {
            StateDot(StateDotState.Warning, size = 6.dp)
        }
        Text(
            modelLabel,
            style = DsType.std14Strong,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        effort?.let {
            Text(it.name, style = DsType.small13, color = colors.labelTertiary, maxLines = 1)
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .clip(DsShapes.cube)
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.small, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        Icon(icon, contentDescription = null, tint = colors.labelTertiary, modifier = Modifier.size(14.dp))
        Text(label, style = DsType.small13, color = colors.labelSecondary, maxLines = 1)
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * The tab strip. Hand-rolled rather than Material's `TabRow` so the indicator can animate to the
 * label's own width instead of the full tab slot — with only two short labels, a full-width
 * indicator reads as a highlight bar rather than an underline.
 */
@Composable
private fun ChatTabRow(tab: ChatTab, onTabChange: (ChatTab) -> Unit) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.large),
    ) {
        TabLabel(stringResource(R.string.chat_tab), tab == ChatTab.Chat) { onTabChange(ChatTab.Chat) }
        TabLabel(stringResource(R.string.trajectory_title), tab == ChatTab.Trajectory) {
            onTabChange(ChatTab.Trajectory)
        }
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.borderL1),
    )
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DsTheme.colors
    val indicator by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = DsAnimations.tabSwap,
        label = "tabIndicator",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = DsSpacing.small),
    ) {
        Text(
            text,
            style = DsType.tabText,
            color = if (selected) colors.accent else colors.labelTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.accent.copy(alpha = indicator)),
        )
    }
}
