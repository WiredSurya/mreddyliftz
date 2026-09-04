package com.mreddy.liftz.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.db.ExerciseType
import com.mreddy.liftz.data.json.JsonPort
import com.mreddy.liftz.data.prefs.ThemeMode
import com.mreddy.liftz.domain.Calories
import com.mreddy.liftz.ui.common.factoryOf
import kotlinx.coroutines.launch

/**
 * SETTINGS (behind the profile icon in the bottom nav).
 *
 *  - editable per-parameter increments (water / protein / carbs / calories)
 *  - daily goals
 *  - per-exercise rolling window
 *  - JSON import / export through the system file picker
 *
 * The workout rep increment is fixed at 1 by design and is deliberately not editable here.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = factoryOf { SettingsViewModel(LiftzApp.repo(), LiftzApp.instance.database) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTo(context.contentResolver, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(context.contentResolver, it, JsonPort.ImportMode.OVERWRITE) } }

    val mergeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(context.contentResolver, it, JsonPort.ImportMode.MERGE) } }

    // Writes the reference template that ships in assets/ out to wherever the user picks, so the
    // documented schema is reachable from inside the app instead of only from the repo.
    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.saveBundledTemplate(context, it) } }

    val scope = rememberCoroutineScope()
    val themeMode by LiftzApp.prefs().themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        message?.let { text ->
            item {
                Card { Text(text, Modifier.padding(12.dp), fontSize = 13.sp) }
            }
        }

        /* ---- increments ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Increments per tap", fontWeight = FontWeight.SemiBold)
                    Text(
                        "How much one +/- press adds",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    StepperRow("Water", "${state.increments.waterMl} ml", step = 50,
                        onChange = { d ->
                            viewModel.saveIncrements(
                                state.increments.copy(
                                    waterMl = (state.increments.waterMl + d).coerceAtLeast(10)
                                )
                            )
                        })
                    StepperRow("Protein", "${state.increments.proteinG} g", step = 1,
                        onChange = { d ->
                            viewModel.saveIncrements(
                                state.increments.copy(
                                    proteinG = (state.increments.proteinG + d).coerceAtLeast(1)
                                )
                            )
                        })
                    StepperRow("Carbs", "${state.increments.carbsG} g", step = 1,
                        onChange = { d ->
                            viewModel.saveIncrements(
                                state.increments.copy(
                                    carbsG = (state.increments.carbsG + d).coerceAtLeast(1)
                                )
                            )
                        })
                    StepperRow("Fat", "${state.increments.fatG} g", step = 1,
                        onChange = { d ->
                            viewModel.saveIncrements(
                                state.increments.copy(
                                    fatG = (state.increments.fatG + d).coerceAtLeast(1)
                                )
                            )
                        })
                    StepperRow("Calories", "${state.increments.calories} kcal", step = 25,
                        onChange = { d ->
                            viewModel.saveIncrements(
                                state.increments.copy(
                                    calories = (state.increments.calories + d).coerceAtLeast(5)
                                )
                            )
                        })
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Reps always step by 1. Not editable on purpose.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        /* ---- goals ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Daily goals", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    StepperRow("Water", "${state.goals.waterMl} ml", step = 250,
                        onChange = { d ->
                            viewModel.saveGoals(
                                state.goals.copy(waterMl = (state.goals.waterMl + d).coerceAtLeast(0))
                            )
                        })
                    StepperRow("Protein", "${state.goals.proteinG} g", step = 5,
                        onChange = { d ->
                            viewModel.saveGoals(
                                state.goals.copy(proteinG = (state.goals.proteinG + d).coerceAtLeast(0))
                            )
                        })
                    StepperRow("Carbs", "${state.goals.carbsG} g", step = 5,
                        onChange = { d ->
                            viewModel.saveGoals(
                                state.goals.copy(carbsG = (state.goals.carbsG + d).coerceAtLeast(0))
                            )
                        })
                    StepperRow("Fat", "${state.goals.fatG} g", step = 5,
                        onChange = { d ->
                            viewModel.saveGoals(
                                state.goals.copy(fatG = (state.goals.fatG + d).coerceAtLeast(0))
                            )
                        })
                    StepperRow("Calories", "${state.goals.calories} kcal", step = 50,
                        onChange = { d ->
                            viewModel.saveGoals(
                                state.goals.copy(calories = (state.goals.calories + d).coerceAtLeast(0))
                            )
                        })

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Calculate calories from macros", fontSize = 14.sp)
                            Text(
                                "4 kcal/g protein and carbs, 9 kcal/g fat. Turn this off only if " +
                                    "you would rather type calories in yourself.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.goals.autoCalcCalories,
                            onCheckedChange = { on ->
                                viewModel.saveGoals(state.goals.copy(autoCalcCalories = on))
                            }
                        )
                    }
                    if (state.goals.autoCalcCalories) {
                        val fromGoals = Calories.fromMacros(
                            state.goals.proteinG, state.goals.carbsG, state.goals.fatG
                        )
                        Text(
                            "Your macro goals add up to $fromGoals kcal against a " +
                                "${state.goals.calories} kcal target." +
                                if (fromGoals < state.goals.calories - 100)
                                    "  Raise fat or carbs to close the gap."
                                else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        /* ---- rolling window per exercise ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Rolling window", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Consecutive qualifying sessions needed before a level up is suggested. " +
                            "Also the window used for time estimates.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    state.exercises
                        .filter { it.exercise.type != ExerciseType.CORE }
                        .forEach { ewp ->
                            StepperRow(
                                label = ewp.exercise.name,
                                value = "${ewp.exercise.rollingWindow}",
                                step = 1,
                                onChange = { d ->
                                    viewModel.setRollingWindow(
                                        ewp.exercise.id,
                                        ewp.exercise.rollingWindow + d
                                    )
                                }
                            )
                        }
                }
            }
        }

        /* ---- import / export ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Routine data", fontWeight = FontWeight.SemiBold)
                    Text(
                        "JSON, readable by you and by any future AI session with no prior context.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { exportLauncher.launch("mreddyliftz_export.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export to JSON") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import (replace routine)") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { mergeLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import (merge)") }
                }
            }
        }

        /* ---- appearance ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Appearance", fontWeight = FontWeight.SemiBold)
                    Text(
                        "System follows your phone's own light/dark setting.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { scope.launch { LiftzApp.prefs().setThemeMode(mode) } },
                                label = {
                                    Text(
                                        mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        /* ---- JSON schema reference ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("JSON template", fontWeight = FontWeight.SemiBold)
                    Text(
                        "The reference file showing every supported key, with an instructions " +
                            "block inside it. Save a copy to edit your routine by hand, then " +
                            "bring it back in with Import above.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { templateLauncher.launch("mreddyliftz_template.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save template file") }
                }
            }
        }

        /* ---- support ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Support", fontWeight = FontWeight.SemiBold)
                    Text(
                        SUPPORT_EMAIL,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            // Opens the user's mail app with a blank draft. Nothing is sent from
                            // here — the user writes and sends it themselves.
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                putExtra(Intent.EXTRA_SUBJECT, "mreddyLiftz support")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Email support") }
                }
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }
}

/** Contact address shown in Settings and used for the Play Store listing's support contact. */
const val SUPPORT_EMAIL = "suryapatrimath@gmail.com"

@Composable
private fun StepperRow(label: String, value: String, step: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        IconButton(onClick = { onChange(-step) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text(value, Modifier.width(84.dp), fontSize = 13.sp)
        IconButton(onClick = { onChange(step) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}
