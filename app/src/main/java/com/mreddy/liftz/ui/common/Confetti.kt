package com.mreddy.liftz.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dependency-free confetti burst. Particles are generated once, then a single animated float
 * drives every position, so the whole thing is one recomposing Canvas and nothing else.
 */
@Composable
fun ConfettiBurst(
    playing: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 70,
    durationMillis: Int = 1400,
    colors: List<Color> = listOf(
        Color(0xFFF5C542),  // gold
        Color(0xFF2ECC71),  // green
        Color(0xFFFFFFFF),
        Color(0xFF6EC6FF)
    ),
    onFinished: () -> Unit = {}
) {
    val particles = remember(playing) {
        val rng = Random(System.currentTimeMillis())
        List(particleCount) {
            Particle(
                angleRad = rng.nextDouble(0.0, Math.PI * 2).toFloat(),
                speed = rng.nextDouble(0.35, 1.0).toFloat(),
                spin = rng.nextDouble(-1.0, 1.0).toFloat(),
                size = rng.nextDouble(4.0, 10.0).toFloat(),
                color = colors[rng.nextInt(colors.size)]
            )
        }
    }

    val t by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis, easing = LinearEasing),
        label = "confetti",
        finishedListener = { if (it >= 1f) onFinished() }
    )

    if (!playing && t == 0f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2f, size.height * 0.42f)
        val reach = size.minDimension * 0.9f
        particles.forEach { p ->
            val distance = reach * p.speed * t
            // Simple ballistic arc: outward + gravity pulling down over time.
            val x = origin.x + cos(p.angleRad) * distance
            val y = origin.y + sin(p.angleRad) * distance + (reach * 0.55f * t * t)
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawRect(
                color = p.color.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(p.size, p.size * (1.6f + p.spin))
            )
        }
    }
}

private data class Particle(
    val angleRad: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val color: Color
)
