package com.mreddy.liftz.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The crown that sits UNDER the green fill of a 100% day and gets revealed by a radial wipe.
 *
 * Drawn as a path rather than shipped as an asset so it scales cleanly into a tiny calendar cell.
 */
@Composable
fun CrownReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF5C542),
    durationMillis: Int = 550
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = LinearEasing),
        label = "crownReveal"
    )
    Canvas(modifier = modifier) {
        if (progress <= 0.01f) return@Canvas
        // Radial wipe: clip everything outside a circle that grows from the centre.
        val maxRadius = kotlin.math.hypot(size.width, size.height) / 2f
        val circle = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxRadius * progress
                )
            )
        }
        clipPath(circle) {
            drawCrown(color = color, alpha = progress)
        }
    }
}

/** Crown path normalised to the current [DrawScope] size, centred with a little padding. */
fun DrawScope.drawCrown(color: Color, alpha: Float = 1f) {
    val pad = size.minDimension * 0.18f
    val w = size.width - pad * 2
    val h = size.height - pad * 2
    val boxSize = Size(w, h)

    translate(left = pad, top = pad) {
        val p = Path().apply {
            // Classic 3-peak crown: base band + three spikes + two valleys.
            moveTo(0f, boxSize.height * 0.78f)                       // bottom-left of band
            lineTo(0f, boxSize.height * 0.30f)                       // up the left edge
            lineTo(boxSize.width * 0.25f, boxSize.height * 0.55f)    // valley 1
            lineTo(boxSize.width * 0.50f, boxSize.height * 0.16f)    // centre peak
            lineTo(boxSize.width * 0.75f, boxSize.height * 0.55f)    // valley 2
            lineTo(boxSize.width, boxSize.height * 0.30f)            // right spike
            lineTo(boxSize.width, boxSize.height * 0.78f)            // down the right edge
            close()
        }
        drawPath(p, color = color, alpha = alpha)

        // Base band, slightly detached, sells the "crown" read at small sizes.
        drawRect(
            color = color,
            alpha = alpha,
            topLeft = Offset(0f, boxSize.height * 0.86f),
            size = Size(boxSize.width, boxSize.height * 0.14f)
        )
    }
}
