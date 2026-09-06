package com.mreddy.liftz.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.domain.SetTiming
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.theme.LiftzGreen
import com.mreddy.liftz.ui.theme.LiftzOrange
import com.mreddy.liftz.ui.theme.crownGold
import kotlin.math.roundToInt

/**
 * Progress and statistics — the page that was missing entirely.
 *
 * Everything here is derived from what is already logged; nothing is estimated or invented. When
 * there is no history the screen says so plainly rather than drawing empty charts, because a
 * zeroed graph reads like a bug on a fresh install.
 */
@Composable
fun StatsScreen(
    /** True while this page is on screen — see the note on ProfileScreen. */
    isActive: Boolean = true,
    onExerciseClick: (String) -> Unit = {},
    viewModel: StatsViewModel = viewModel(
        factory = factoryOf { StatsViewModel(LiftzApp.repo()) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(isActive) { if (isActive) viewModel.reload() }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val stats = state.stats ?: return

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Progress", style = MaterialTheme.typography.headlineSmall)
        }

        if (!stats.hasAnything) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nothing logged yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Finish a workout or log a macro and this page fills in. It only " +
                                "ever shows what you actually recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            return@LazyColumn
        }

        /* ---- training load & fatigue ---- */
        state.insights?.let { ins ->
            if (ins.sessionsAnalysed > 0) {
                item { TrainingCard(ins) }
            }
        }

        /* ---- consistency ---- */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Consistency", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Stat("Streak", "${stats.currentStreak}", "workouts")
                        Stat("Best", "${stats.longestStreak}", "workouts")
                        Stat("Crowns", "${stats.crownDays}", "days", crownGold())
                    }
                    stats.completionRate?.let { rate ->
                        Column {
                            Row {
                                Text(
                                    "Workouts completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${stats.workoutsCompleted}/${stats.workoutsPlanned}" +
                                        "  (${(rate * 100).roundToInt()}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { rate },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = LiftzGreen
                            )
                        }
                    }
                }
            }
        }

        /* ---- strength progression ---- */
        item { Text("Strength", style = MaterialTheme.typography.titleMedium) }
        items(stats.exercises.filter { it.sessions > 0 || it.personalRecord != null }) { ex ->
            ExerciseStatCard(ex) { onExerciseClick(ex.exerciseId) }
        }
        if (stats.exercises.none { it.sessions > 0 }) {
            item {
                Text(
                    "No completed sessions yet, so there is nothing to trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        /* ---- macro averages ---- */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily averages", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Across ${stats.trackedDays} tracked " +
                            if (stats.trackedDays == 1) "day" else "days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AvgRow("Water", stats.avgWaterMl, stats.goals.waterMl, "ml")
                    AvgRow("Protein", stats.avgProteinG, stats.goals.proteinG, "g")
                    AvgRow("Carbs", stats.avgCarbsG, stats.goals.carbsG, "g")
                    AvgRow("Fat", stats.avgFatG, stats.goals.fatG, "g")
                    AvgRow("Calories", stats.avgCalories, stats.goals.calories, "kcal")
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, tint: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = tint ?: MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AvgRow(label: String, value: Int, target: Int, unit: String) {
    val pct = if (target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                "$value / $target $unit",
                style = MaterialTheme.typography.bodySmall,
                color = if (value >= target) LiftzGreen
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (value >= target) LiftzGreen else LiftzOrange
        )
    }
}

@Composable
private fun ExerciseStatCard(ex: LiftzRepository.ExerciseStat, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ex.readyToAdvance) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (ex.readyToAdvance) {
                    Text(
                        "READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = crownGold(),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                buildString {
                    ex.levelLabel?.let { append(it) }
                    ex.weightKg?.let {
                        if (isNotEmpty()) append(" · ")
                        append(if (it % 1.0 == 0.0) "${it.toInt()} kg" else "$it kg")
                    }
                    if (isNotEmpty()) append(" · ")
                    append("${ex.sessions} ${if (ex.sessions == 1) "session" else "sessions"}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ex.personalRecord?.let {
                    Text(
                        "PR $it reps",
                        style = MaterialTheme.typography.labelMedium,
                        color = crownGold()
                    )
                }
                ex.lastReps?.let {
                    Text(
                        "Last $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                ex.atTopOfLadder -> Text(
                    "Top of the ladder",
                    style = MaterialTheme.typography.labelSmall,
                    color = crownGold()
                )
                ex.readyToAdvance -> Text(
                    "Ready to move up — confirm it on the exercise screen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                else -> Column {
                    Text(
                        "${ex.qualifyingStreak}/${ex.windowNeeded} qualifying sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ex.progressToNext },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = LiftzOrange
                    )
                }
            }
        }
    }
}


/**
 * What the stopwatch bought.
 *
 * Every row here is omitted rather than zeroed when its data does not exist yet. Showing
 * "0:00 average rest" to somebody who has never started the stopwatch would be worse than showing
 * nothing: it reads as a measurement rather than an absence, and it is the kind of quietly wrong
 * number that makes a whole stats page untrustworthy.
 */
@Composable
private fun TrainingCard(ins: LiftzRepository.TrainingInsights) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Training load", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Stat("Time trained", SetTiming.formatLong(ins.totalTrainingMs), "total")
                Stat("Reps", "${ins.totalReps}", "logged")
                Stat("Per week", "%.1f".format(ins.sessionsPerWeek), "sessions")
            }

            ins.totalVolumeKg?.let {
                MetricRow("Volume moved", "${it.roundToInt()} kg")
            }
            ins.avgSessionMs?.let {
                MetricRow("Average exercise length", SetTiming.format(it))
            }

            if (ins.hasTiming) {
                Text(
                    "From timing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ins.avgSetMs?.let { MetricRow("Average set", SetTiming.format(it)) }
                ins.avgRestMs?.let { MetricRow("Average rest between sets", SetTiming.format(it)) }
                ins.density?.let {
                    MetricRow("Working vs resting", "${(it * 100).roundToInt()}% working")
                }
                ins.avgSecondsPerRep?.let {
                    MetricRow("Tempo", "%.1f s per rep".format(it))
                }
                ins.tempoSlope?.let { slope ->
                    MetricRow(
                        "Slow-down per set",
                        if (slope <= 0.02) "none — pace holds"
                        else "+%.1f s per rep".format(slope),
                        if (slope > 0.3) LiftzOrange else null
                    )
                }
            } else {
                Text(
                    "Tap the play button on a set to time it. Once a few sets are timed, rest, " +
                        "tempo and fatigue show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Deliberately outside the timing block: this one works on every session ever
            // logged, because it needs nothing but rep counts.
            ins.repDropOff?.let { drop ->
                MetricRow(
                    "Fatigue: last set vs first",
                    if (drop <= 0f) "no drop-off"
                    else "-${(drop * 100).roundToInt()}% reps",
                    if (drop > 0.35f) LiftzOrange else null
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
