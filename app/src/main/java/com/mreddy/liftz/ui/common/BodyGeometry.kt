package com.mreddy.liftz.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.mreddy.liftz.domain.MuscleGroup
import kotlin.math.cos
import kotlin.math.sin

/*
 * Geometry for the anatomical body map, in a 100 x 240 space.
 *
 * Drawn from paths rather than shipped as artwork. Anatomy illustrations of the quality this
 * wants are licensed stock, and beyond the licensing the deeper problem is coverage: a fixed
 * image set can only illustrate a fixed exercise library, and this app lets people invent their
 * own movements. Drawing it means an exercise created ten seconds ago gets the same diagram as
 * a built-in one.
 *
 * The layout follows standard anatomical placement — where the deltoid sits relative to the
 * pectoral is not a matter of style — but every curve here is authored for this app.
 */

const val BODY_W = 100f
const val BODY_H = 240f

/** Smooth closed outline through the given points, mirrored about the vertical centre line. */
private fun mirroredOutline(leftHalf: List<Offset>): Path = Path().apply {
    val right = leftHalf.reversed().map { Offset(BODY_W - it.x, it.y) }
    val all = leftHalf + right
    moveTo(all.first().x, all.first().y)
    // Quadratics through midpoints: the joins stay smooth without hand-tuning control points for
    // every one of forty vertices.
    for (i in 1 until all.size) {
        val prev = all[i - 1]
        val cur = all[i]
        val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
        quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    close()
}

/** An ellipse, optionally rotated — the workhorse for muscle bellies. */
fun blob(cx: Float, cy: Float, rx: Float, ry: Float, rotDeg: Float = 0f): Path = Path().apply {
    val steps = 36
    val r = Math.toRadians(rotDeg.toDouble())
    val cosR = cos(r).toFloat()
    val sinR = sin(r).toFloat()
    for (i in 0..steps) {
        val t = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
        val x0 = rx * cos(t.toDouble()).toFloat()
        val y0 = ry * sin(t.toDouble()).toFloat()
        val x = cx + x0 * cosR - y0 * sinR
        val y = cy + x0 * sinR + y0 * cosR
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Mirror a path built on the left half over to the right. */
fun mirrored(build: () -> Path): Path = Path().apply {
    val left = build()
    addPath(left)
    val m = androidx.compose.ui.graphics.Matrix()
    m.translate(BODY_W, 0f, 0f)
    m.scale(-1f, 1f, 1f)
    val right = Path().apply { addPath(left) }
    right.transform(m)
    addPath(right)
}

fun roundedBlock(l: Float, t: Float, r: Float, b: Float, radius: Float = 2.5f): Path = Path().apply {
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            Rect(l, t, r, b),
            androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
    )
}

/* ---------------------------------------------------------------------------------------------
 * SILHOUETTE
 * ------------------------------------------------------------------------------------------ */

/** Head, drawn separately so the jaw can taper without complicating the body outline. */
fun headPath(): Path = Path().apply {
    addOval(Rect(39f, 4f, 61f, 32f))
}

/**
 * The body outline: neck, shoulders, arms held slightly away from the torso, tapered waist,
 * legs and feet. Points run down the LEFT side; the right is mirrored.
 */
fun bodyOutline(): Path = mirroredOutline(
    listOf(
        Offset(50f, 28f),    // base of skull
        Offset(44f, 33f),    // neck left
        Offset(43f, 42f),
        Offset(33f, 47f),    // trapezius slope out to the shoulder
        Offset(25f, 54f),    // deltoid cap
        Offset(21f, 64f),
        Offset(20f, 80f),    // upper arm
        Offset(19f, 98f),    // elbow
        Offset(16f, 116f),   // forearm
        Offset(15f, 132f),   // wrist
        Offset(13f, 142f),   // hand
        Offset(16f, 152f),
        Offset(22f, 150f),
        Offset(25f, 134f),   // back up the inside of the arm
        Offset(28f, 116f),
        Offset(30f, 98f),
        Offset(31f, 76f),    // armpit
        Offset(33f, 62f),
        Offset(33f, 88f),    // ribs
        Offset(35f, 104f),   // waist
        Offset(32f, 120f),   // hip
        Offset(31f, 138f),
        Offset(33f, 158f),   // thigh
        Offset(36f, 178f),   // knee
        Offset(34f, 192f),   // calf
        Offset(37f, 212f),
        Offset(36f, 226f),   // ankle
        Offset(33f, 234f),   // foot
        Offset(45f, 234f),
        Offset(45f, 224f),
        Offset(46f, 200f),
        Offset(47f, 176f),
        Offset(48f, 150f),   // inner thigh
        Offset(49f, 128f)    // crotch
    )
)

/* ---------------------------------------------------------------------------------------------
 * MUSCLES
 * ------------------------------------------------------------------------------------------ */

data class MusclePart(val muscle: MuscleGroup, val path: Path)

fun frontMuscles(): List<MusclePart> = listOf(
    MusclePart(MuscleGroup.NECK, roundedBlock(44f, 30f, 56f, 42f, 4f)),
    MusclePart(MuscleGroup.TRAPS, Path().apply {
        // The upper trap is visible from the front as the slope from neck to shoulder.
        addPath(mirrored { blob(38f, 45f, 9f, 4.5f, -20f) })
    }),
    MusclePart(MuscleGroup.SHOULDERS, mirrored { blob(27f, 57f, 7f, 9f, 8f) }),
    MusclePart(MuscleGroup.CHEST, mirrored { blob(41f, 60f, 9f, 7.5f, -8f) }),
    MusclePart(MuscleGroup.BICEPS, mirrored { blob(25f, 80f, 5.5f, 12f, 3f) }),
    MusclePart(MuscleGroup.FOREARMS, mirrored { blob(22f, 112f, 5f, 14f, 4f) }),
    // Rectus abdominis: segmented, because one flat block reads as a plate rather than abs.
    MusclePart(MuscleGroup.ABS, Path().apply {
        var y = 74f
        repeat(4) {
            addPath(roundedBlock(43.5f, y, 49.2f, y + 8.5f, 2f))
            addPath(roundedBlock(50.8f, y, 56.5f, y + 8.5f, 2f))
            y += 10f
        }
    }),
    MusclePart(MuscleGroup.OBLIQUES, mirrored { blob(38.5f, 95f, 3.8f, 13f, 6f) }),
    MusclePart(MuscleGroup.QUADS, mirrored { blob(40f, 150f, 8f, 22f, 2f) }),
    MusclePart(MuscleGroup.ADDUCTORS, mirrored { blob(46f, 145f, 3.2f, 16f, -3f) }),
    MusclePart(MuscleGroup.CALVES, mirrored { blob(40f, 198f, 5.5f, 15f, 1f) })
)

fun backMuscles(): List<MusclePart> = listOf(
    // Trapezius: the big diamond from neck to mid-back.
    MusclePart(MuscleGroup.TRAPS, Path().apply {
        moveTo(50f, 32f)
        lineTo(33f, 48f)
        lineTo(38f, 62f)
        lineTo(50f, 92f)
        lineTo(62f, 62f)
        lineTo(67f, 48f)
        close()
    }),
    MusclePart(MuscleGroup.SHOULDERS, mirrored { blob(27f, 57f, 7f, 9f, 8f) }),
    MusclePart(MuscleGroup.UPPER_BACK, mirrored { blob(38f, 66f, 5.5f, 6f, -10f) }),
    MusclePart(MuscleGroup.TRICEPS, mirrored { blob(25f, 80f, 5.5f, 12f, 3f) }),
    MusclePart(MuscleGroup.FOREARMS, mirrored { blob(22f, 112f, 5f, 14f, 4f) }),
    // Lats: wide at the armpit, tapering into the lower back.
    MusclePart(MuscleGroup.LATS, mirrored {
        Path().apply {
            moveTo(32f, 70f)
            lineTo(41f, 76f)
            lineTo(46f, 100f)
            lineTo(38f, 104f)
            lineTo(33f, 88f)
            close()
        }
    }),
    MusclePart(MuscleGroup.LOWER_BACK, roundedBlock(43f, 100f, 57f, 118f, 4f)),
    MusclePart(MuscleGroup.GLUTES, mirrored { blob(41f, 130f, 8.5f, 9.5f, 0f) }),
    MusclePart(MuscleGroup.HAMSTRINGS, mirrored { blob(40f, 162f, 7.5f, 18f, 1f) }),
    MusclePart(MuscleGroup.CALVES, mirrored { blob(40f, 198f, 5.5f, 15f, 1f) })
)
