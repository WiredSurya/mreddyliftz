package com.mreddy.liftz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import com.mreddy.liftz.data.prefs.ThemeMode
import com.mreddy.liftz.data.sync.LaunchSync
import kotlinx.coroutines.launch
import com.mreddy.liftz.ui.nav.LiftzNavHost
import com.mreddy.liftz.ui.theme.MreddyLiftzTheme

/**
 * Runs the automatic cloud sync once per app start, and surfaces the one outcome that needs a
 * human: the cloud holding newer data than a phone that already has its own history.
 *
 * Everything else is intentionally silent. A backup that succeeded is not news, and interrupting
 * someone opening the app to tell them so would be worse than saying nothing.
 */
@androidx.compose.runtime.Composable
private fun LaunchSyncGate() {
    var conflict by remember { mutableStateOf<LaunchSync.RemoteIsNewer?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Unit key: once per Activity, not on every recomposition or theme flip.
    LaunchedEffect(Unit) {
        when (val outcome = LiftzApp.sync().syncOnLaunch()) {
            is LaunchSync.RemoteIsNewer -> conflict = outcome
            else -> Unit
        }
    }

    conflict?.let { c ->
        AlertDialog(
            onDismissRequest = { if (!busy) conflict = null },
            title = { Text("Newer data in the cloud") },
            text = {
                Text(
                    "Another device backed up more recently than this one. Restoring replaces " +
                        "everything on this phone with that copy — anything logged here since " +
                        "then would be lost.\n\nKeeping this phone's data leaves the cloud copy " +
                        "alone; your next backup from here will overwrite it."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            LiftzApp.sync().restoreNow()
                            busy = false
                            conflict = null
                        }
                    }
                ) { Text(if (busy) "Restoring…" else "Use the cloud copy") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { conflict = null }) {
                    Text("Keep this phone's data")
                }
            }
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by LiftzApp.prefs().themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MreddyLiftzTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LiftzNavHost()
                    LaunchSyncGate()
                }
            }
        }
    }
}
