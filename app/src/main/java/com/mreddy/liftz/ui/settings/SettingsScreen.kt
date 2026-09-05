package com.mreddy.liftz.ui.settings

import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
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
    val scope = rememberCoroutineScope()

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
    // Persistable grant: the folder is chosen once and keeps working across reboots.
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch { LiftzApp.syncPrefs().setBackupFolder(uri.toString()) }
        }
    }

    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.saveBundledTemplate(context, it) } }

    val themeMode by LiftzApp.prefs().themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val showOffline by LiftzApp.prefs().showOfflineIndicator.collectAsState(initial = false)
    val syncStatus by LiftzApp.sync().status.collectAsState(initial = null)
    var syncBusy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

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

        /* ---- starting point ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Example routine", fontWeight = FontWeight.SemiBold)
                    Text(
                        "A worked full-body split using every feature the app has — a level " +
                            "ladder, a weighted lift, and untracked core work. Load it to poke " +
                            "at, then edit or delete whatever you like. New installs start empty " +
                            "on purpose, so this is opt-in.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.loadExample() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Load the example routine") }
                }
            }
        }

        /* ---- google account & cloud sync ---- */
        item { AccountCard() }

        /* ---- backup & restore ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Backup", fontWeight = FontWeight.SemiBold)
                    val st = syncStatus
                    Text(
                        when {
                            st?.accountEmail != null ->
                                "Backing up to your Google account. Sign in on another phone " +
                                    "and your history comes with you."
                            st?.folderUri != null ->
                                "Backing up to the folder you chose. Point that at a Drive, " +
                                    "Dropbox or OneDrive folder and their app syncs it off the " +
                                    "phone for you — this app never sees your password."
                            else ->
                                "Stored on this device only right now. It survives a bad import " +
                                    "or a wrong edit, but NOT uninstalling the app or losing the " +
                                    "phone. Sign in above, or choose a folder below."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (st?.folderUri != null) "Change backup folder"
                            else "Choose a backup folder"
                        )
                    }
                    if (st?.folderUri != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                readableFolder(context, st.folderUri),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                scope.launch { LiftzApp.syncPrefs().setBackupFolder(null) }
                            }) { Text("Use device", fontSize = 11.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        st?.lastBackupAtMs?.let { "Last backup: ${relativeTime(it)}" }
                            ?: "No backup taken yet",
                        fontSize = 12.sp
                    )
                    st?.lastRestoreAtMs?.let {
                        Text(
                            "Last restore: ${relativeTime(it)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    st?.lastError?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            syncBusy = true
                            scope.launch {
                                LiftzApp.sync().backUpNow()
                                syncBusy = false
                            }
                        },
                        enabled = !syncBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (syncBusy) "Working…" else "Back up now") }

                    Spacer(Modifier.height(6.dp))
                    if (!confirmRestore) {
                        OutlinedButton(
                            onClick = { confirmRestore = true },
                            enabled = !syncBusy && st?.lastBackupAtMs != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restore from backup") }
                    } else {
                        // Restore OVERWRITES, so it asks first rather than being one tap away
                        // from replacing a routine the user just spent time on.
                        Text(
                            "This replaces your current routine and settings with the backup. " +
                                "Anything changed since then is lost.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { confirmRestore = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    confirmRestore = false
                                    syncBusy = true
                                    scope.launch {
                                        LiftzApp.sync().restoreNow()
                                        syncBusy = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Overwrite") }
                        }
                    }
                }
            }
        }

        /* ---- connectivity preview ---- */
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Offline indicator", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Shows a banner and a pulsing icon when the phone has no " +
                                    "internet. Off by default because nothing in the app needs " +
                                    "a connection yet — it does not even hold the internet " +
                                    "permission. It is here ready for when cloud sync ships.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showOffline,
                            onCheckedChange = { on ->
                                scope.launch { LiftzApp.prefs().setShowOfflineIndicator(on) }
                            }
                        )
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

/**
 * Turns a SAF tree URI into something a person recognises.
 *
 * Raw tree URIs look like
 * `content://com.android.providers.downloads.documents/tree/downloads`, which is meaningless on
 * a settings screen. Ask the provider for the folder's real display name and fall back to the
 * last path segment only if it will not answer.
 */
private fun readableFolder(context: Context, uri: String): String {
    val name = runCatching {
        DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name
    }.getOrNull()
    if (!name.isNullOrBlank()) return "Saving to: $name"
    val tail = Uri.decode(uri).substringAfterLast('/')
    return "Saving to: ${tail.ifBlank { "the folder you chose" }}"
}

/** "3 minutes ago" / "yesterday" — precise enough for a backup timestamp, no library needed. */
private fun relativeTime(epochMs: Long): String {
    val delta = System.currentTimeMillis() - epochMs
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        hours < 24 -> "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
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
