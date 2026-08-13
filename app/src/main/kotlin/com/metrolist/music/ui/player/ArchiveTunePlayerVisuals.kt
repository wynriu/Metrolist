/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.PlayerBackgroundStyle
import kotlin.math.max

/**
 * Renders ArchiveTune-inspired motion backgrounds from the current album palette.
 * The animation is canvas-only, so it remains inexpensive and does not allocate bitmaps
 * while playback is running.
 */
@Composable
fun ArchiveTunePlayerBackdrop(
    style: PlayerBackgroundStyle,
    palette: List<Color>,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    if (style != PlayerBackgroundStyle.LIVE_MESH && style != PlayerBackgroundStyle.GLOW_ANIMATED) {
        return
    }

    val colors =
        if (palette.isNotEmpty()) {
            palette
        } else {
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
            )
        }
    val first = colors[0]
    val second = colors.getOrElse(1) { MaterialTheme.colorScheme.tertiary }
    val third = colors.getOrElse(2) { MaterialTheme.colorScheme.secondary }
    val transition = rememberInfiniteTransition(label = "archiveTunePlayerBackdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (style == PlayerBackgroundStyle.LIVE_MESH) 12_000 else 7_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "archiveTuneBackdropPhase",
    )

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .alpha(alpha.coerceIn(0f, 1f)),
    ) {
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(first.copy(alpha = 0.72f), second.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.76f)),
                ),
        )
        when (style) {
            PlayerBackgroundStyle.LIVE_MESH -> drawLiveMesh(first, second, third, phase)
            PlayerBackgroundStyle.GLOW_ANIMATED -> drawAnimatedGlow(first, second, phase)
            else -> Unit
        }
        drawRect(color = Color.Black.copy(alpha = 0.16f))
    }
}

@Composable
fun ArchiveTuneArtworkFrame(
    palette: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val first = palette.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val second = palette.getOrNull(1) ?: MaterialTheme.colorScheme.tertiary
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        listOf(first.copy(alpha = 0.72f), second.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.58f)),
                    ),
                )
                .padding(4.dp),
        content = content,
    )
}

private fun DrawScope.drawLiveMesh(
    first: Color,
    second: Color,
    third: Color,
    phase: Float,
) {
    val longestSide = max(size.width, size.height)
    val radius = longestSide * 0.74f
    val reversePhase = 1f - phase

    drawCircle(
        color = first.copy(alpha = 0.54f),
        radius = radius,
        center = Offset(size.width * (0.08f + 0.28f * phase), size.height * (0.14f + 0.22f * reversePhase)),
    )
    drawCircle(
        color = second.copy(alpha = 0.48f),
        radius = radius * 0.88f,
        center = Offset(size.width * (0.86f - 0.25f * phase), size.height * (0.66f + 0.17f * phase)),
    )
    drawCircle(
        color = third.copy(alpha = 0.42f),
        radius = radius * 0.72f,
        center = Offset(size.width * (0.42f + 0.14f * reversePhase), size.height * (0.92f - 0.46f * phase)),
    )
}

private fun DrawScope.drawAnimatedGlow(
    first: Color,
    second: Color,
    phase: Float,
) {
    val longestSide = max(size.width, size.height)
    val pulse = 0.76f + phase * 0.26f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(first.copy(alpha = 0.8f), first.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * (0.28f + phase * 0.2f), size.height * 0.34f),
            radius = longestSide * pulse,
        ),
        radius = longestSide * pulse,
        center = Offset(size.width * (0.28f + phase * 0.2f), size.height * 0.34f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(second.copy(alpha = 0.64f), second.copy(alpha = 0.1f), Color.Transparent),
            center = Offset(size.width * (0.76f - phase * 0.14f), size.height * 0.74f),
            radius = longestSide * (1.03f - phase * 0.18f),
        ),
        radius = longestSide * (1.03f - phase * 0.18f),
        center = Offset(size.width * (0.76f - phase * 0.14f), size.height * 0.74f),
    )
}
