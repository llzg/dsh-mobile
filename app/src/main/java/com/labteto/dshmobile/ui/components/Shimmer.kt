package com.labteto.dshmobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sweeps [brush] as a moving glare band across the content, looping once every
 * [duration] ms. The band is drawn with [BlendMode.SrcAtop], so it only tints the
 * content's own pixels (ideal for a shimmering text row).
 */
fun Modifier.shimmer(
    brush: Brush,
    bandWidth: Dp = 300.dp,
    duration: Int = 2600,
): Modifier = composed {
    val density = LocalDensity.current
    val bandPx = with(density) { bandWidth.toPx() }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    drawWithContent {
        drawContent()
        val sweep = size.width + bandPx
        drawRect(
            brush = brush,
            topLeft = Offset(-bandPx + progress * sweep, 0f),
            size = Size(bandPx, size.height),
            blendMode = BlendMode.SrcAtop,
        )
    }
}
