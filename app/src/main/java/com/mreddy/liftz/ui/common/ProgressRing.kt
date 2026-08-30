package com.mreddy.liftz.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pie-chart style ring that fills as sets are completed.
 *
 * At 100% the stroke flips to gold, which is the "brief gold flash" the celebration sits on top of.
 */
@Composable
fun SetProgressRing(
    progress: Float,
    setsDone: Int,
    setsTotal: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 140.dp,
    strokeWidth: Dp = 14.dp,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    completeColor: Color = MaterialTheme.colorScheme.secondary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "ringProgress"
    )
    val complete = progress >= 1f
    val color = if (complete) completeColor else ringColor

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth.toPx(),
                size.height - strokeWidth.toPx()
            )
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        Text(
            text = "$setsDone/$setsTotal",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (complete) completeColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
