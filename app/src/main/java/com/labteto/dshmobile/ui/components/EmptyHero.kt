package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/**
 * Centered empty state: whale mark, hero headline, optional subtitle, a mono
 * "Preview" pill, and suggestion chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyHero(
    headline: String,
    subtitle: String?,
    chips: List<String> = emptyList(),
    onChipClick: (String) -> Unit = {},
) {
    val colors = DsTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhaleMark(Modifier.size(64.dp))
        Text(
            headline,
            style = DsType.hero26,
            color = colors.labelPrimary,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Text(
                it,
                style = DsType.base16,
                color = colors.labelSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Preview",
            style = DsType.xsmall12.copy(fontFamily = DsType.codeFont, color = colors.accent),
            color = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.accentTertiary)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        if (chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip ->
                    DsPill(text = chip, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

/** 64dp accentTertiary disc with a simplified white whale silhouette. */
@Composable
private fun WhaleMark(modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    Box(modifier.clip(CircleShape).background(colors.accentTertiary), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0.80f * w, 0.32f * h)
                cubicTo(0.64f * w, 0.26f * h, 0.46f * w, 0.30f * h, 0.36f * w, 0.40f * h)
                cubicTo(0.28f * w, 0.47f * h, 0.19f * w, 0.48f * h, 0.16f * w, 0.54f * h)
                cubicTo(0.14f * w, 0.60f * h, 0.18f * w, 0.66f * h, 0.27f * w, 0.63f * h)
                cubicTo(0.35f * w, 0.71f * h, 0.49f * w, 0.75f * h, 0.63f * w, 0.68f * h)
                cubicTo(0.71f * w, 0.64f * h, 0.76f * w, 0.57f * h, 0.80f * w, 0.50f * h)
                lineTo(0.95f * w, 0.36f * h)
                lineTo(0.88f * w, 0.46f * h)
                lineTo(0.95f * w, 0.60f * h)
                close()
            }
            drawPath(path, color = Color.White)
            drawCircle(color = colors.accent, radius = 0.05f * w, center = Offset(0.30f * w, 0.50f * h))
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EmptyHeroPreview() {
    DshTheme {
        EmptyHero(
            headline = "Nothing running yet",
            subtitle = "Ask the harness anything, or pick a suggestion below.",
            chips = listOf("Summarize this repo", "Run the test suite", "Explain a diff"),
            onChipClick = {},
        )
    }
}
