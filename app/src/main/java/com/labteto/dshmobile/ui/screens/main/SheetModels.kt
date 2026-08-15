package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.ui.components.DsBottomSheet
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.coroutines.launch

/** Model picker, grouped by provider, with each model's reasoning tiers inline. */
@Composable
internal fun ModelsSheet(
    models: SessionModelsValue?,
    store: SessionStore,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    DsBottomSheet(title = stringResource(R.string.models_title), onDismiss = onDismiss) {
        if (models == null) {
            Text(stringResource(R.string.common_loading), style = DsType.std14, color = colors.labelTertiary)
            return@DsBottomSheet
        }
        val current = models.current
        if (!models.routable) {
            Text(
                stringResource(R.string.models_unroutable),
                style = DsType.small13,
                color = colors.warnLabel,
            )
        }
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            models.groups.forEach { group ->
                SectionHeader(group.name)
                group.models.forEach { model ->
                    val isCurrent = current.provider == group.id && current.model == model.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { store.selectModel(group.id, model.id) } }
                            .padding(vertical = DsSpacing.xsmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.name,
                                style = DsType.std14Strong,
                                color = if (isCurrent) colors.accent else colors.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            model.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = DsType.caption11, color = colors.labelTertiary)
                            }
                        }
                        if (isCurrent) {
                            Spacer(Modifier.width(DsSpacing.small))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.models_current),
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    model.reasoning?.efforts?.takeIf { it.isNotEmpty() }?.let { efforts ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, bottom = DsSpacing.xsmall),
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
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
            models.failures.forEach { failure ->
                Text(
                    stringResource(R.string.err_model_unavailable, "${failure.name}: ${failure.message}"),
                    style = DsType.caption11,
                    color = colors.warnLabel,
                )
            }
        }
    }
}
