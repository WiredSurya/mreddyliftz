package com.mreddy.liftz.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.TimeEstimator
import com.mreddy.liftz.ui.common.CrownReveal
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.theme.LiftzGold
import com.mreddy.liftz.ui.theme.LiftzGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * What the day added up to, once the work is done.
 *
 * Read-only by design: it reports, it does not re-open sessions. The only actions are confirming
 * or dismissing a progression prompt, which is the natural moment to decide — the set that earned
 * it is thirty seconds old.
 */
@Composable
fun SummaryScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    viewModel: SummaryViewModel = viewModel(
        key = "summary-${date.toEpochDay()}",
        factory = factoryOf { SummaryViewModel(LiftzApp.repo(), date) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMM") }
    val summary = state.summary

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Workout summary", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        date.format(dateFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (summary == null || summary.lines.isEmpty()) {
            item {
                Text(
                    "Nothing was planned for this day.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            return@LazyColumn
        }

        item { HeadlineCard(summary) }

        if (summary.personalRecords.isNotEmpty()) {
            item { RecordsCard(summary) }
        }

        items(summary.lines) { line -> ExerciseLineCard(line, onClick = { onExerciseClick(line.exerciseId) }) }

        // Progression prompts land here so the decision is made while the effort is fresh.
        items(summary.lines.mapNotNull { it.pendingSuggestion }) { suggestion ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ready to move up", fontWeight = FontWeight.SemiBold, color = LiftzGold)
                    Text(suggestion.rationale, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.acceptSuggestion(suggestion) }) {
                            Text("Confirm")
                        }
                        TextButton(onClick = { viewModel.dismissSuggestion(suggestion) }) {
                            Text("Not yet")
                        }
                    }
                }
            }
        }

        item { Box(Modifier.size(24.dp)) }
    }
}

@Composable
private fun HeadlineCard(s: LiftzRepository.DaySummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.allComplete) "Workout complete" else "Partly done",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (s.allComplete) LiftzGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${s.exercisesCompleted} of ${s.lines.size} exercises",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // The same crown the calendar uses, so a finished day reads the same in both places.
                CrownReveal(visible = s.allComplete, modifier = Modifier.size(40.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("Sets", s.totalSets.toString())
                Stat("Reps", s.totalReps.toString())
                Stat("Time", TimeEstimator.format(s.totalSeconds))
                Stat("Rest", TimeEstimator.format(s.totalRestSeconds))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordsCard(s: LiftzRepository.DaySummary) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (s.personalRecords.size == 1) "New personal record" else "New personal records",
                fontWeight = FontWeight.SemiBold,
                color = LiftzGold
            )
            s.personalRecords.forEach { line ->
                Text(
                    buildString {
                        append(line.name)
                        line.levelLabel?.let { append(" · $it") }
                        append(" — ${line.topReps} reps")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ExerciseLineCard(line: LiftzRepository.DaySummaryLine, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (line.completed) LiftzGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.name, fontWeight = FontWeight.Medium)
                    if (line.isPersonalRecord) {
                        Text("  PR", color = LiftzGold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    buildString {
                        append("${line.setsLogged}/${line.setsPlanned} sets")
                        append(" · ${line.totalReps} reps")
                        line.levelLabel?.let { append(" · $it") }
                        line.weightKg?.let { append(" · ${fmtKg(it)} kg") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (line.seconds > 0) {
                Text(
                    TimeEstimator.format(line.seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun fmtKg(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
