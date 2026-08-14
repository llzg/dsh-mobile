package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/** Compact h24 pill chip; [selected] adds an inset border, [warn] uses the warn palette. */
@Composable
fun DsPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    warn: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = DsTheme.colors
    val shape = if (warn) DsShapes.pillFull else DsShapes.pill
    val background = when {
        warn -> colors.warnTertiary
        selected -> colors.hoverSolid
        else -> colors.bgLayer2
    }
    val contentColor = if (warn) colors.warnLabel else colors.labelSecondary
    Surface(
        onClick = onClick ?: {},
        modifier = modifier.height(24.dp),
        enabled = onClick != null,
        shape = shape,
        color = background,
        contentColor = contentColor,
        border = if (selected && !warn) BorderStroke(1.dp, colors.borderL2) else null,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = DsType.xsmall12,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DsPillPreview() {
    DshTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DsPill("Plain", onClick = {})
            DsPill("Selected", selected = true, onClick = {})
            DsPill("Warn", warn = true, onClick = {})
        }
    }
}
