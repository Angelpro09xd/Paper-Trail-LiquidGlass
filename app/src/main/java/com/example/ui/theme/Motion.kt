package com.example.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object PaperTrailMotion {
    // Use for content/size expanding into view — a touch of spring overshoot feels alive.
    fun <T> expressiveExpand(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Use for content/size collapsing or leaving view — no overshoot, clean settle.
    fun <T> expressiveCollapse(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Use for press/tap feedback (scale-down on press).
    fun pressScaleSpec(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // Use for opacity/fade transitions only — never spring a fade, it reads as flicker.
    val fadeIn: FiniteAnimationSpec<Float> = tween(150)
    val fadeOut: FiniteAnimationSpec<Float> = tween(120)

    // Use for full-screen navigation transitions between destinations.
    fun screenEnter(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    fun screenExit(): FiniteAnimationSpec<IntOffset> = tween(200)

    const val PRESS_SCALE_DOWN = 0.98f
}
