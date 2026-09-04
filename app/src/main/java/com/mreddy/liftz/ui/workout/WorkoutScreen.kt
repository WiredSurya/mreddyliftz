package com.mreddy.liftz.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.db.QueueState
import com.mreddy.liftz.data.db.SuggestionKind
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.Calories
import com.mreddy.liftz.domain.TimeEstimator
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.theme.LiftzGold
import com.mreddy.liftz.ui.common.RollingNumber
import com.mreddy.liftz.ui.theme.LiftzGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * DAY SCREEN.
 *
 * On a workout day this is the workout queue. On a rest day the queue is empty and only the macro
 * card shows, which is why the calendar can open every day, not just workout days.
 */
@Composable
fun WorkoutScreen(
    date: LocalDate,
    onExerciseClick: (String) -> Unit,
    onSummaryClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutViewModel = viewModel(
        key = "workout-${date.toEpochDay()}",
        factory = factoryOf { WorkoutViewModel(LiftzApp.repo(), date) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMM") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text(date.format(dateFormat), style = MaterialTheme.typography.headlineSmall)
                val completion = state.completion
                if (completion != null) {
                    Text(
                        "${completion.hits} / ${completion.denominator} goals hit" +
                            if (completion.isCrown) "  crown day" else "",
                        color = if (completion.isCrown) LiftzGold
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        /* ---- progression suggestions waiting for confirmation ---- */
        items(state.suggestions, key = { it.id }) { suggestion ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    val name = state.rows.firstOrNull { it.plan.exercise.id == suggestion.exerciseId }
                        ?.plan?.exercise?.name ?: suggestion.exerciseId
                    Text("Level up: $name", fontWeight = FontWeight.SemiBold)
                    Text(
                        when (suggestion.kind) {
                            SuggestionKind.LEVEL_UP ->
                                "${suggestion.fromLevelKey} to ${suggestion.toLevelKey}"
                            SuggestionKind.WEIGHT_UP ->
                                "${suggestion.fromWeightKg} kg to ${suggestion.toWeightKg} kg"
                        },
                        fontSize = 13.sp
                    )
                    Text(
                        suggestion.rationale,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = { viewModel.acceptSuggestion(suggestion) }) {
                            Text("Move me up")
                        }
                        TextButton(onClick = { viewModel.dismissSuggestion(suggestion) }) {
                            Text("Not yet")
                        }
                    }
                }
            }
        }

        /* ---- macros ---- */
        item { MacroCard(state = state, viewModel = viewModel) }

        /* ---- workout progress header ---- */
        if (state.rows.isNotEmpty()) {
            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Workout", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "~${state.remainingLabel} left",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.workoutProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = LiftzGreen
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.setsDone} / ${state.setsPlanned} sets",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        /* ---- the queue ---- */
        items(state.rows, key = { it.plan.exercise.id }) { row ->
            QueueRowCard(row = row, onClick = { onExerciseClick(row.plan.exercise.id) })
        }

        /* ---- post-workout summary ---- */
        if (state.rows.isNotEmpty()) {
            item {
                val allDone = state.rows.all { it.state == QueueState.COMPLETED }
                Card(onClick = onSummaryClick, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (allDone) "Workout complete" else "Workout summary",
                                fontWeight = FontWeight.SemiBold,
                                color = if (allDone) LiftzGreen
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (allDone) "See what the session added up to"
                                else "See how the day is going so far",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    "Rest day. Macros only.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Spotify-queue style row.
 *
 *   COMPLETED   dimmed, tick on the left
 *   IN_PROGRESS highlighted, play glyph, shows sets done
 *   UPCOMING    plain, shows planned sets and time estimate
 */
@Composable
private fun QueueRowCard(row: QueueRow, onClick: () -> Unit) {
    val highlighted = row.state == QueueState.IN_PROGRESS
    val dimmed = row.state == QueueState.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted)
                LiftzGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (row.state) {
                QueueState.COMPLETED -> Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Completed",
                    tint = LiftzGreen, modifier = Modifier.size(20.dp)
                )
                QueueState.IN_PROGRESS -> Icon(
                    Icons.Filled.PlayArrow, contentDescription = "In progress",
                    tint = LiftzGreen, modifier = Modifier.size(20.dp)
                )
                QueueState.UPCOMING -> Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.plan.exercise.name,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                val subtitle = buildString {
                    val level = row.plan.currentLevel?.displayName
                    val weight = row.plan.exercise.currentWeightKg
                    when {
                        level != null -> append(level)
                        weight != null -> append("${weight.toInt()} kg")
                    }
                    if (isNotEmpty()) append("  ")
                    append("${row.setsDone}/${row.setsPlanned} sets")
                }
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                TimeEstimator.format(row.estimatedSeconds),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (row.state == QueueState.IN_PROGRESS) {
            LinearProgressIndicator(
                progress = { row.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = LiftzGreen
            )
        }
    }
}

/** Water / protein / carbs / calories, each with a minus and a plus that step by the increment. */
@Composable
private fun MacroCard(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    val log = state.dailyLog
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Macros", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            MacroRow(
                label = "Water", unit = "ml",
                current = log?.waterMl ?: 0, target = state.goals.waterMl,
                step = state.increments.waterMl,
                onStep = { viewModel.adjustMacro(LiftzRepository.Macro.WATER, it) }
            )
            MacroRow(
                label = "Protein", unit = "g",
                current = log?.proteinG ?: 0, target = state.goals.proteinG,
                step = state.increments.proteinG,
                onStep = { viewModel.adjustMacro(LiftzRepository.Macro.PROTEIN, it) }
            )
            MacroRow(
                label = "Carbs", unit = "g",
                current = log?.carbsG ?: 0, target = state.goals.carbsG,
                step = state.increments.carbsG,
                onStep = { viewModel.adjustMacro(LiftzRepository.Macro.CARBS, it) }
            )
            MacroRow(
                label = "Fat", unit = "g",
                current = log?.fatG ?: 0, target = state.goals.fatG,
                step = state.increments.fatG,
                onStep = { viewModel.adjustMacro(LiftzRepository.Macro.FAT, it) }
            )
            if (state.goals.autoCalcCalories) {
                // Derived, so there is nothing to tap: showing +/- here would imply you can set
                // calories independently of the macros they are computed from.
                DerivedCalorieRow(
                    current = Calories.fromMacros(
                        proteinG = log?.proteinG ?: 0,
                        carbsG = log?.carbsG ?: 0,
                        fatG = log?.fatG ?: 0
                    ),
                    target = state.goals.calories
                )
            } else {
                MacroRow(
                    label = "Calories", unit = "kcal",
                    current = log?.calories ?: 0, target = state.goals.calories,
                    step = state.increments.calories,
                    onStep = { viewModel.adjustMacro(LiftzRepository.Macro.CALORIES, it) }
                )
            }
        }
    }
}

/** Calories when they are computed rather than entered: same shape as a MacroRow, no controls. */
@Composable
private fun DerivedCalorieRow(current: Int, target: Int) {
    val hit = target <= 0 || current >= target
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Calories", fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumber(
                    value = current,
                    fontSize = 12.sp,
                    color = if (hit) LiftzGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    " / $target kcal",
                    fontSize = 12.sp,
                    color = if (hit) LiftzGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "auto",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MacroRow(
    label: String,
    unit: String,
    current: Int,
    target: Int,
    step: Int,
    onStep: (Int) -> Unit
) {
    val hit = target <= 0 || current >= target
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rolls like a dial so a +/- tap is unmistakable at a glance.
                RollingNumber(
                    value = current,
                    fontSize = 12.sp,
                    color = if (hit) LiftzGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    " / $target $unit",
                    fontSize = 12.sp,
                    color = if (hit) LiftzGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { onStep(-step) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Minus $step $unit")
        }
        Text("$step", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onStep(step) }) {
            Icon(Icons.Filled.Add, contentDescription = "Plus $step $unit")
        }
    }
}
