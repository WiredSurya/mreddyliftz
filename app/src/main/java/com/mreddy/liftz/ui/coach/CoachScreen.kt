package com.mreddy.liftz.ui.coach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.json.JsonPort
import com.mreddy.liftz.domain.Coach
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.theme.LiftzGreen
import com.mreddy.liftz.ui.theme.LiftzOrange
import com.mreddy.liftz.ui.theme.crownGold

/**
 * Coach.
 *
 * Two halves, deliberately kept distinct:
 *
 *  - What the app can work out by itself, from rules over your own logged numbers. Instant,
 *    offline, free, and it never repeats a canned tip because every line is generated from your
 *    data or not shown at all.
 *  - A hand-off to whichever LLM you already use. Rather than bolting a model into the app —
 *    which would mean an API key, a running cost, and your training history leaving the device on
 *    someone else's terms — this exports your history with a written briefing attached, so you
 *    can paste it into any assistant and import the result straight back.
 */
@Composable
fun CoachScreen(
    viewModel: CoachViewModel = viewModel(
        factory = factoryOf { CoachViewModel(LiftzApp.repo(), LiftzApp.instance.database) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pasted by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportForLlm(context.contentResolver, it) } }

    if (state.loading) {
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
            Spacer(Modifier.height(12.dp))
            Text("Coach", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Read from what you have actually logged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.message?.let { msg ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { viewModel.clearMessage() }) { Text("OK") }
                    }
                }
            }
        }

        items(state.insights) { insight -> InsightCard(insight) }

        /* ---- hand-off to an external LLM ---- */
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(LiftzOrange)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Design workouts with AI", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "There is no AI model inside this app, on purpose — it would mean an API " +
                            "key, a running cost, and your training history leaving the device. " +
                            "Instead you can hand your data to whichever assistant you already " +
                            "use, and bring the result back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Step(1, "Export below. You get one file: a written briefing, then your full history as JSON.")
                    Step(2, "Paste the whole file into ChatGPT, Claude, Gemini — whatever you use.")
                    Step(3, "It answers in plain language, then hands back a complete JSON file.")
                    Step(4, "Save that, then use Settings → Routine data → Import to load it.")
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { exportLauncher.launch("mreddyliftz_for_ai.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export my data + briefing") }
                    OutlinedButton(
                        onClick = { copyBriefing(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Copy just the briefing") }
                    Text(
                        "The briefing tells the model the schema rules — levels are ordered, " +
                            "records are per level, rep increments are always 1 — so what comes " +
                            "back is actually importable instead of plausible-looking prose.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        /* ---- bring the answer back ---- */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste the plan back in", fontWeight = FontWeight.Bold)
                    Text(
                        "Most free assistants hand you a code block, not a file. Paste the whole " +
                            "reply here — prose and ``` fences included — and the JSON is found " +
                            "inside it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        placeholder = { Text("Paste here", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pasted = readClipboard(context) ?: pasted },
                            modifier = Modifier.weight(1f)
                        ) { Text("From clipboard") }
                        OutlinedButton(
                            onClick = { pasted = "" },
                            enabled = pasted.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.importPasted(pasted, JsonPort.ImportMode.MERGE)
                                pasted = ""
                            },
                            enabled = pasted.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Merge") }
                        Button(
                            onClick = {
                                viewModel.importPasted(pasted, JsonPort.ImportMode.OVERWRITE)
                                pasted = ""
                            },
                            enabled = pasted.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Replace") }
                    }
                    Text(
                        "Merge keeps what you already have and adds to it. Replace swaps the " +
                            "routine definition out entirely — your logged history is kept either " +
                            "way.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$n",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InsightCard(insight: Coach.Insight) {
    val accent = when (insight.kind) {
        Coach.Kind.WIN -> crownGold()
        Coach.Kind.ACTION -> LiftzOrange
        Coach.Kind.WATCH -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    insight.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Text(insight.title, fontWeight = FontWeight.SemiBold)
                Text(
                    insight.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyBriefing(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(
        ClipData.newPlainText("mreddyLiftz coach briefing", CoachViewModel.LLM_BRIEFING)
    )
}
