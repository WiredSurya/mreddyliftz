package com.mreddy.liftz.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.data.db.SetType
import com.mreddy.liftz.ui.common.factoryOf

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Build or edit one exercise by hand.
 *
 * This is the other half of "how does a routine come to exist" now that the app ships blank —
 * the Coach screen's AI hand-off being the first. Both write the same tables, so an exercise
 * created here is indistinguishable from an imported one afterwards.
 */
@Composable
fun ExerciseEditorScreen(
    exerciseId: String?,
    onDone: () -> Unit,
    viewModel: ExerciseEditorViewModel = viewModel(
        key = "editor-${exerciseId ?: "new"}",
        factory = factoryOf { ExerciseEditorViewModel(LiftzApp.repo(), exerciseId) }
    )
) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(s.saved) { if (s.saved) onDone() }

    if (s.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    if (s.existingId == null) "New exercise" else "Edit exercise",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        item {
            OutlinedTextField(
                value = s.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        /* ---- how it progresses ---- */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How it gets harder", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeChip("Levels", s.type == ExerciseType.BODYWEIGHT_PROGRESSION) {
                            viewModel.update { it.copy(type = ExerciseType.BODYWEIGHT_PROGRESSION) }
                        }
                        TypeChip("Weight", s.type == ExerciseType.WEIGHTED) {
                            viewModel.update { it.copy(type = ExerciseType.WEIGHTED) }
                        }
                        TypeChip("Untracked", s.type == ExerciseType.CORE) {
                            viewModel.update { it.copy(type = ExerciseType.CORE) }
                        }
                    }
                    Text(
                        when (s.type) {
                            ExerciseType.BODYWEIGHT_PROGRESSION ->
                                "Moves up a ladder you define — negatives, band assisted, full " +
                                    "rep and so on. Records are kept per level, so dropping back " +
                                    "a rung compares you against that rung's own history."
                            ExerciseType.WEIGHTED ->
                                "Moves up by adding weight once you hold the top of the rep range."
                            ExerciseType.CORE ->
                                "No progression logic at all. Plain set and rep logging, for " +
                                    "things that rotate too much to track."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        /* ---- levels ladder ---- */
        if (s.type == ExerciseType.BODYWEIGHT_PROGRESSION) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Levels, easiest first", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap the circle to mark where you are now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        s.levelNames.forEachIndexed { index, label ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = s.currentLevelIndex == index,
                                    onClick = { viewModel.update { it.copy(currentLevelIndex = index) } },
                                    label = { Text(if (s.currentLevelIndex == index) "now" else "${index + 1}") }
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = label,
                                    onValueChange = { viewModel.setLevel(index, it) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = { viewModel.removeLevel(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove level")
                                }
                            }
                        }
                        OutlinedButton(onClick = { viewModel.addLevel() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add level")
                        }
                    }
                }
            }
        }

        /* ---- weight ---- */
        if (s.type == ExerciseType.WEIGHTED) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Weight", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = s.currentWeightKg,
                                onValueChange = { v -> viewModel.update { it.copy(currentWeightKg = v) } },
                                label = { Text("Starting kg") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = s.weightIncrementKg,
                                onValueChange = { v -> viewModel.update { it.copy(weightIncrementKg = v) } },
                                label = { Text("Step kg") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        /* ---- sets and reps ---- */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sets and reps", fontWeight = FontWeight.SemiBold)
                    Stepper("Sets", s.plannedSets) { d ->
                        viewModel.update { it.copy(plannedSets = (it.plannedSets + d).coerceIn(1, 12)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        TypeChip("Fixed reps", s.setType == SetType.FIXED_REP) {
                            viewModel.update { it.copy(setType = SetType.FIXED_REP) }
                        }
                        TypeChip("To failure", s.setType == SetType.TO_FAILURE) {
                            viewModel.update { it.copy(setType = SetType.TO_FAILURE) }
                        }
                    }
                    if (s.setType == SetType.FIXED_REP) {
                        Stepper("Goal reps", s.goalReps) { d ->
                            viewModel.update { it.copy(goalReps = (it.goalReps + d).coerceIn(1, 100)) }
                        }
                    }
                    if (s.type != ExerciseType.CORE) {
                        Stepper("Rep range min", s.hypertrophyMin) { d ->
                            viewModel.update { it.copy(hypertrophyMin = (it.hypertrophyMin + d).coerceIn(1, 100)) }
                        }
                        Stepper("Rep range max", s.hypertrophyMax) { d ->
                            viewModel.update { it.copy(hypertrophyMax = (it.hypertrophyMax + d).coerceIn(1, 100)) }
                        }
                        Stepper("Sessions before level up", s.rollingWindow) { d ->
                            viewModel.update { it.copy(rollingWindow = (it.rollingWindow + d).coerceIn(1, 30)) }
                        }
                        Text(
                            "Hit ${s.hypertrophyMax} reps on every set for ${s.rollingWindow} " +
                                "sessions in a row and the app offers the next step.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Stepper("Rest per set (s)", s.restSecondsPerSet, step = 15) { d ->
                        viewModel.update { it.copy(restSecondsPerSet = (it.restSecondsPerSet + d).coerceIn(0, 600)) }
                    }
                }
            }
        }

        /* ---- schedule ---- */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Which days", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAY_LABELS.forEachIndexed { index, label ->
                            val day = index + 1
                            FilterChip(
                                selected = day in s.daysOfWeek,
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        "A day becomes a training day as soon as something is scheduled on it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = s.formDescription,
                onValueChange = { v -> viewModel.update { it.copy(formDescription = v) } },
                label = { Text("Form notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            s.problem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { viewModel.save() },
                enabled = s.problem == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (s.existingId == null) "Add to routine" else "Save changes") }

            if (s.existingId != null) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { viewModel.delete() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete exercise") }
                Text(
                    "Deleting removes it from your plan. Sessions you already logged are kept.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun Stepper(label: String, value: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange(-step) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(step) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}
