package com.labteto.dshmobile.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Animation specifications for consistent motion throughout the app.
 * Use these specs instead of hardcoded animation parameters.
 */
object DsAnimations {
    /** Fast spring animation for quick interactions (e.g., button press) */
    val fastSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /** Normal spring animation for general transitions */
    val normalSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    /** Press scale animation spec */
    val pressScale = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    
    /** Scale animation duration for press effects */
    const val scaleDuration = 100
    
    /** Fade animation duration for opacity changes */
    const val fadeDuration = 150
    
    /** Standard transition duration for layout changes */
    const val transitionDuration = 200
    
    /** Scale values for interactive elements */
    object Scale {
        const val normal = 1f
        const val pressed = 0.95f
    }
}
