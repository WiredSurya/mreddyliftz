package com.mreddy.liftz.ui.exercise

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.db.SetType
import com.mreddy.liftz.domain.SetTiming
import com.mreddy.liftz.domain.TimeEstimator
import com.mreddy.liftz.ui.common.ConfettiBurst
import com.mreddy.liftz.ui.common.SetProgressRing
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.theme.LiftzGold
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * EXERCISE SCREEN (full screen on tap from the workout queue).
 *
 * Top to bottom: record/level header, collapsible form description, the pie-chart ring,
 * the set-by-set logging list, and the cumulative rest timer.
 *
 * Completing the last set fills the ring, flashes gold, fires confetti and a haptic buzz, then
 * returns to the workout screen on its own.
 */
@Composable
fun ExerciseScreen(
    exerciseId: String,
    date: LocalDate,
    onFinished: () -> Unit,
    viewModel: ExerciseViewModel = viewModel(
        key = "exercise-$exerciseId-${date.toEpochDay()}",
        factory = factoryOf { ExerciseViewModel(LiftzApp.repo(), exerciseId, date) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var formExpanded by remember { mutableStateOf(false) }

    // Gold flash: the whole background warms for a beat when the ring completes.
    val flashColor by animateColorAsState(
        targetValue = if (state.celebrate) LiftzGold.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(220),
        label = "goldFlash"
    )

    // Haptic buzz + auto-return.
    LaunchedEffect(state.celebrate) {
        if (state.celebrate) {
            buzz(context)
            delay(1500)
            viewModel.celebrationShown()
            if (state.pendingSuggestion == null) onFinished()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(Modifier.fillMaxSize().background(flashColor)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(state.exerciseName, style = MaterialTheme.typography.headlineSmall)

            /* ---- traditional record / current progression level ---- */
            RecordHeader(state)

            Spacer(Modifier.height(8.dp))

            /* ---- level ladder: any rung selectable, including regressing ---- */
            if (state.levels.isNotEmpty()) {
                Text(
                    "Level",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.levels.forEach { level ->
                        FilterChip(
                            selected = level.levelKey == state.currentLevelKey,
                            onClick = { viewModel.selectLevel(level.levelKey) },
                            label = { Text(level.displayName, fontSize = 12.sp) }
                        )
                    }
                }
            }

            /* ---- weighted exercises: manual weight nudge ---- */
            state.currentWeightKg?.let { weight ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weight", fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = { viewModel.adjustWeight(-(state.weightIncrementKg ?: 2.0)) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Less weight")
                    }
                    Text("${trim(weight)} kg", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { viewModel.adjustWeight(state.weightIncrementKg ?: 2.0) }) {
                        Icon(Icons.Filled.Add, contentDescription = "More weight")
                    }
                }
            }

            /* ---- collapsible form description ---- */
            if (state.formDescription.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { formExpanded = !formExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Form", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(
                                if (formExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (formExpanded) "Collapse" else "Expand"
                            )
                        }
                        AnimatedVisibility(visible = formExpanded) {
                            Column {
                                Spacer(Modifier.height(6.dp))
                                Text(state.formDescription, fontSize = 14.sp)
                                if (state.notes.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        state.notes,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /* ---- the ring ---- */
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                SetProgressRing(
                    progress = state.ringProgress,
                    setsDone = state.setsLogged,
                    setsTotal = state.rows.size
                )
            }

            /* ---- what this works ---- */
            if (state.primaryMuscle != null || state.secondaryMuscles.isNotEmpty()) {
                MuscleCard(state)
                Spacer(Modifier.height(10.dp))
            }

            /* ---- live stopwatches ---- */
            StopwatchCard(state = state)

            Spacer(Modifier.height(10.dp))

            /* ---- set list ---- */
            state.rows.forEachIndexed { index, row ->
                SetRow(
                    index = index,
                    row = row,
                    running = state.runningSetIndex == index,
                    otherRunning = state.runningSetIndex != null && state.runningSetIndex != index,
                    elapsedMs = state.setElapsedMs,
                    onStart = { viewModel.startSet(index) },
                    onMinus = { viewModel.bumpReps(index, -1) },   // rep increment is fixed at 1
                    onPlus = { viewModel.bumpReps(index, 1) },
                    onComplete = { viewModel.completeSet(index) },
                    onUndo = { viewModel.undoSet(index) }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                state.progressionNote,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))
        }

        /* ---- celebration ---- */
        ConfettiBurst(playing = state.celebrate, modifier = Modifier.fillMaxSize())
    }

    /* ---- progression suggestion, shown after the workout is banked ---- */
    state.pendingSuggestion?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuggestion(); onFinished() },
            title = { Text("Ready to move up") },
            text = {
                Column {
                    Text(suggestion.rationale)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        suggestion.toLevelKey?.let { "New level: $it" }
                            ?: "New weight: ${suggestion.toWeightKg} kg",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.acceptSuggestion(); onFinished() }) { Text("Do it") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSuggestion(); onFinished() }) { Text("Stay here") }
            }
        )
    }
}

/** Current record and level, i.e. the "traditional record / progression level" block. */
@Composable
private fun RecordHeader(state: ExerciseUiState) {
    val levelName = state.levels.firstOrNull { it.levelKey == state.currentLevelKey }?.displayName
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (levelName != null) {
            AssistChip(
                onClick = {},
                label = { Text(levelName, fontSize = 12.sp) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(
                state.personalRecord?.let { "PR at this level: $it reps" } ?: "No record at this level yet",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                state.lastSessionTopReps?.let { "Last time: $it reps  target ${state.hypertrophyMin}-${state.hypertrophyMax}" }
                    ?: "Baseline session: whatever you do today sets the mark",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One set row: type badge, rep stepper, tick to log, undo once logged. */
@Composable
private fun SetRow(
    index: Int,
    row: SetRowState,
    /** True while THIS set's stopwatch is running. */
    running: Boolean,
    /** True when some other set is running, so this one cannot be started. */
    otherRunning: Boolean,
    elapsedMs: Long,
    onStart: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onComplete: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                running -> MaterialTheme.colorScheme.primaryContainer
                row.logged -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text("${index + 1}", fontSize = 12.sp) }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    if (row.plannedSet.setType == SetType.TO_FAILURE) "To failure" else "Goal reps",
                    fontSize = 13.sp
                )
                val sub = buildString {
                    if (running) {
                        append(SetTiming.format(elapsedMs))
                    } else if (row.logged && row.durationMs > 0) {
                        // Only for timed sets: a 0 means the stopwatch was never started, and
                        // printing "0:00" would claim the set was instantaneous.
                        append(SetTiming.format(row.durationMs))
                    }
                    if (row.plannedSet.label.isNotBlank()) {
                        if (isNotEmpty()) append("  ")
                        append(row.plannedSet.label)
                    }
                    row.targetToBeat?.let {
                        if (isNotEmpty()) append("  ")
                        append("beat $it")
                    }
                    row.plannedSet.levelKeyOverride?.let {
                        if (isNotEmpty()) append("  ")
                        append(it)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Start is offered only for an unlogged set with no other set already running.
            // Logging without ever starting stays possible — timing is optional, not a gate.
            if (!row.logged && !running && !otherRunning) {
                IconButton(onClick = onStart) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Start this set",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onMinus, enabled = !row.logged) {
                Icon(Icons.Filled.Remove, contentDescription = "One less rep")
            }
            Text(
                "${row.reps}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = onPlus, enabled = !row.logged) {
                Icon(Icons.Filled.Add, contentDescription = "One more rep")
            }

            if (row.logged) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo this set")
                }
            } else {
                IconButton(onClick = onComplete) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Log this set",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Two clocks counting UP, side by side.
 *
 * This replaced a countdown that could only say how much of a planned rest budget was left. The
 * problem with that framing is it measures the plan, not the workout: it could never tell you how
 * long a set actually took, so rest was assumed rather than known. Timing the exercise and each
 * set separately makes rest a subtraction — and makes tempo, density and fatigue computable at
 * all. See `domain/SetTiming.kt`.
 */
@Composable
private fun StopwatchCard(state: ExerciseUiState) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Exercise",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    SetTiming.format(state.exerciseElapsedMs),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f)) {
                // One slot, three meanings: the running set, the rest since the last one, or
                // nothing yet. Showing all three at once would be noise on a phone held at
                // arm's length between sets.
                val (label, value, tint) = when {
                    state.runningSetIndex != null -> Triple(
                        "Set ${state.runningSetIndex!! + 1}",
                        SetTiming.format(state.setElapsedMs),
                        MaterialTheme.colorScheme.primary
                    )
                    state.isResting -> Triple(
                        "Resting",
                        SetTiming.format(state.restElapsedMs),
                        MaterialTheme.colorScheme.onSurface
                    )
                    else -> Triple(
                        "Worked",
                        SetTiming.format(state.workedMs),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tint)
            }
        }
    }
}

/** Short double-tap buzz on ring completion. */
private fun buzz(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    val pattern = longArrayOf(0, 60, 70, 140)
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
}

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()


/**
 * The muscles this exercise trains, drawn rather than named.
 *
 * Sits above the stopwatches because it answers "am I on the right screen and doing the right
 * thing" — a question you have before you start, not while you are counting reps.
 */
@Composable
private fun MuscleCard(state: ExerciseUiState) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.mreddy.liftz.ui.common.BodyMap(
                intensity = buildMap {
                    state.primaryMuscle?.let { put(it, 1f) }
                    // Same third-of-a-set weighting the weekly map uses, so the shading here
                    // means the same thing it does on the profile.
                    state.secondaryMuscles.forEach {
                        if (it != state.primaryMuscle) put(it, 0.34f)
                    }
                },
                showLabels = false,
                figureHeight = 118.dp,
                modifier = Modifier.width(150.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                state.primaryMuscle?.let {
                    Text("Works", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                if (state.secondaryMuscles.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Also", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.secondaryMuscles.joinToString(", ") { it.displayName },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
