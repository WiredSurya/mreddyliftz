package com.mreddy.liftz.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mreddy.liftz.domain.MuscleGroup

/**
 * The anatomical body map: a figure per view, each muscle shaded by how hard it was trained.
 *
 * Two jobs, one component. On the profile it summarises a week and makes the gaps in a split
 * visible at a glance — a list of workouts cannot show you that hamstrings have been untouched
 * for nine days, a dark patch on a leg can. On an exercise it shows what that movement hits,
 * which is what makes building your own routine possible without knowing anatomy.
 */
@Composable
fun BodyMap(
    intensity: Map<MuscleGroup, Float>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    /** Sized by HEIGHT: an aspect ratio on a full-width parent makes the figure screen-tall. */
    figureHeight: Dp = 200.dp
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Front" to false, "Back" to true).forEach { (label, isBack) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BodyFigure(intensity, isBack, Modifier.fillMaxWidth().height(figureHeight))
                if (showLabels) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/** One figure. Public so the exercise screen can show a single view when space is tight. */
@Composable
fun BodyFigure(
    intensity: Map<MuscleGroup, Float>,
    back: Boolean,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurface
    // The silhouette must sit clearly BEHIND the muscles. A first attempt used two near-identical
    // cream tones and the whole figure read as one flat blob on paper stock.
    val skin = ink.copy(alpha = 0.13f)
    val edge = ink.copy(alpha = 0.30f)
    val restingMuscle = ink.copy(alpha = 0.26f)
    val hot = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        val scale = size.height / BODY_H
        val dx = (size.width - BODY_W * scale) / 2f

        fun place(p: Path): Path {
            val out = Path().apply { addPath(p) }
            val m = Matrix()
            m.translate(dx, 0f, 0f)
            m.scale(scale, scale, 1f)
            out.transform(m)
            return out
        }

        val body = place(bodyOutline())
        val head = place(headPath())

        drawPath(body, color = skin)
        drawPath(head, color = skin)

        (if (back) backMuscles() else frontMuscles()).forEach { part ->
            val v = intensity[part.muscle] ?: 0f
            drawPath(
                place(part.path),
                // Any training at all lifts a muscle clear of the resting tone: the difference
                // between "a little" and "none" is the signal the whole map exists to show.
                color = if (v <= 0f) restingMuscle else lerp(restingMuscle, hot, 0.45f + 0.55f * v)
            )
        }

        // Outline last so the figure keeps a crisp edge over the muscle fills.
        drawPath(body, color = edge, style = Stroke(width = 1.1f * scale))
        drawPath(head, color = edge, style = Stroke(width = 1.1f * scale))
    }
}
