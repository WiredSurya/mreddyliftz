package com.mreddy.liftz.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.auth.AuthState
import com.mreddy.liftz.ui.common.BodyMap
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.settings.AccountCard
import com.mreddy.liftz.ui.theme.crownGold
import kotlin.math.roundToInt

/**
 * PROFILE — the hub.
 *
 * This used to be the settings screen wearing a profile icon, which meant the first thing anyone
 * saw when tapping their own face was a list of rep increments. Settings is now behind the gear;
 * this page answers "how am I doing" instead of "what can I configure".
 *
 * The body map is the centrepiece rather than decoration. A list of completed workouts cannot
 * show you that you have not touched hamstrings in nine days; a body with a dark patch on it can,
 * at a glance, without reading anything.
 */
@Composable
fun ProfileScreen(
    /**
     * True while this page is the one on screen.
     *
     * The tabs are pages of a pager inside ONE nav destination, so these ViewModels are built
     * once and never rebuilt when you swipe back — a one-shot load in init would show numbers
     * from whenever the app started. Reloading on becoming visible is what makes a set logged
     * thirty seconds ago actually appear.
     */
    isActive: Boolean,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenExercises: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = factoryOf { ProfileViewModel(LiftzApp.repo()) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(isActive) { if (isActive) viewModel.reload() }
    val authState by LiftzApp.auth().state()
        .collectAsState(initial = LiftzApp.auth().currentState())

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        /* ---- header: name + gear ---- */
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val who = authState
                val display = when (who) {
                    is AuthState.SignedIn -> who.displayName ?: who.email?.substringBefore('@')
                    else -> null
                }
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (display ?: "you").take(1).uppercase(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        display ?: "mreddyLiftz",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        if (display != null) "Synced to your account" else "Signed out — local only",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        /* ---- headline numbers ---- */
        state.stats?.let { stats ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HeadlineStat("${stats.workoutsCompleted}", "Workouts", Modifier.weight(1f))
                        HeadlineStat("${stats.currentStreak}", "Streak", Modifier.weight(1f))
                        HeadlineStat("${stats.crownDays}", "Crowns", Modifier.weight(1f), crownGold())
                    }
                }
            }
        }

        /* ---- account: prominent, not buried ---- */
        item { AccountCard(onMoreOptions = onSignIn) }

        /* ---- this week's muscles ---- */
        state.week?.let { week -> item { MuscleWeekCard(week) } }

        /* ---- dashboard ---- */
        item {
            Text("Dashboard", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashTile("Statistics", Icons.AutoMirrored.Filled.ShowChart, Modifier.weight(1f), onOpenStats)
                DashTile("Exercises", Icons.Filled.FitnessCenter, Modifier.weight(1f), onOpenExercises)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashTile("Calendar", Icons.Filled.CalendarMonth, Modifier.weight(1f), onOpenCalendar)
                Spacer(Modifier.weight(1f))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeadlineStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MuscleWeekCard(week: com.mreddy.liftz.data.repo.LiftzRepository.MuscleWeek) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${week.totalSets} sets",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BodyMap(intensity = week.intensity, modifier = Modifier.fillMaxWidth())

            if (week.totalSets == 0) {
                Text(
                    "Nothing logged in the last seven days. Train something and it lights up here.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Worked, most-hit first.
                val worked = week.trained.take(6)
                if (worked.isNotEmpty()) {
                    Text(
                        worked.joinToString("  ") {
                            "${it.displayName} ${week.setsPerMuscle[it]?.roundToInt() ?: 0}"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // The gaps. Capped at four, because listing all thirteen untrained muscles on a
                // rest-heavy week is noise, not insight.
                val gaps = week.untouched.take(4)
                if (gaps.isNotEmpty()) {
                    Text(
                        "Nothing this week: " + gaps.joinToString(", ") { it.displayName },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (week.unclassifiedExercises.isNotEmpty()) {
                Text(
                    "Not on the map yet: ${week.unclassifiedExercises.joinToString(", ")}. " +
                        "Set their muscles when you edit them.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
