package com.mreddy.liftz.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.prefs.ExperienceLevel
import com.mreddy.liftz.data.prefs.TrainingGoal
import com.mreddy.liftz.data.prefs.TrainingPlace
import com.mreddy.liftz.data.prefs.TrainingProfile
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * First-run setup.
 *
 * Runs on first launch rather than only after creating an account, because the app is fully
 * usable signed out and every answer here is useful either way — the goals it derives are worth
 * having whether or not anything syncs.
 *
 * Skippable at every step, and re-runnable from Settings. A wall of compulsory questions between
 * somebody and the app they just installed is how you lose them before they log a single set.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var profile by remember { mutableStateOf(TrainingProfile()) }

    val steps = 5

    /**
     * @param keepWeight false when the last step was SKIPPED rather than confirmed.
     *
     * The weight slider shows a default of 70 kg before it is touched, so "Done" has to treat
     * that displayed number as the answer — otherwise anyone who simply agreed with it would get
     * a screen full of numbers that silently counted for nothing. Skip is the opposite intent and
     * must leave it unset.
     */
    fun finish(keepWeight: Boolean) {
        val weight = if (keepWeight) (profile.bodyWeightKg ?: DEFAULT_WEIGHT_KG) else null
        val finalProfile = profile.copy(completed = true, bodyWeightKg = weight)
        scope.launch {
            LiftzApp.profilePrefs().save(finalProfile)
            // The answers have to change something, or this was a form rather than setup.
            LiftzApp.repo().applyProfileGoals(finalProfile.bodyWeightKg, finalProfile.goal)
            onDone()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Progress pips
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(steps) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (step) {
                0 -> {
                    Q("What are you training for?")
                    TrainingGoal.entries.forEach { g ->
                        Choice(g.label, null, profile.goal == g) {
                            profile = profile.copy(goal = g)
                        }
                    }
                }
                1 -> {
                    Q("How long have you been training?")
                    ExperienceLevel.entries.forEach { e ->
                        Choice(e.label, e.detail, profile.experience == e) {
                            profile = profile.copy(experience = e)
                        }
                    }
                }
                2 -> {
                    Q("How many days a week?")
                    (1..6).forEach { d ->
                        Choice(
                            if (d == 1) "1 day" else "$d days",
                            if (d == 3) "A good default" else null,
                            profile.daysPerWeek == d
                        ) { profile = profile.copy(daysPerWeek = d) }
                    }
                }
                3 -> {
                    Q("Where do you train?")
                    TrainingPlace.entries.forEach { p ->
                        Choice(p.label, p.detail, profile.place == p) {
                            profile = profile.copy(place = p)
                        }
                    }
                }
                4 -> {
                    Q("Roughly what do you weigh?")
                    Text(
                        "Used to work out sensible protein and calorie targets. You can change " +
                            "them any time, and skipping just leaves the defaults.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val kg = profile.bodyWeightKg ?: DEFAULT_WEIGHT_KG
                    Text(
                        "${kg.roundToInt()} kg",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = kg.toFloat(),
                        onValueChange = { profile = profile.copy(bodyWeightKg = it.toDouble()) },
                        valueRange = 35f..160f
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (step == steps - 1) finish(keepWeight = false) else step++ }) {
                Text("Skip")
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step == steps - 1) finish(keepWeight = true) else step++ }) {
                Text(if (step == steps - 1) "Done" else "Continue")
            }
        }
    }
}

/** Shown on the slider before it is touched, and taken as the answer if Done is pressed. */
private const val DEFAULT_WEIGHT_KG = 70.0

@Composable
private fun Q(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun Choice(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 16.sp
            )
            detail?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
