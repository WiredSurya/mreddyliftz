package com.mreddy.liftz.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.auth.AuthState
import com.mreddy.liftz.data.auth.SignInOutcome
import kotlinx.coroutines.launch

/**
 * Google account + cloud sync, as one block in Settings.
 *
 * The copy here does real work. Signing in is the only thing in this app that sends your data
 * off the phone, so the card says so in the same breath as the button rather than burying it in
 * a privacy policy nobody opens.
 */
@Composable
fun AccountCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authState by LiftzApp.auth().state()
        .collectAsState(initial = LiftzApp.auth().currentState())
    val cloudOn by LiftzApp.syncPrefs().cloudSyncEnabled.collectAsState(initial = true)

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Account", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))

            when (val who = authState) {
                is AuthState.SignedOut -> {
                    Text(
                        "Sign in with Google to sync your training history across devices — " +
                            "install the app on a new phone, sign in, and everything is there.\n\n" +
                            "This is the only feature that sends your data off this phone. " +
                            "Signed out, the app is fully local and works exactly the same.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null) {
                                message = "Couldn't open the Google sign-in sheet."
                                return@OutlinedButton
                            }
                            busy = true
                            message = null
                            scope.launch {
                                when (val r = LiftzApp.auth().signIn(activity)) {
                                    is SignInOutcome.Success -> {
                                        // First sync immediately, so the card stops claiming
                                        // "never backed up" the moment you sign in.
                                        message = when (val s = LiftzApp.sync().syncOnLaunch()) {
                                            is com.mreddy.liftz.data.sync.LaunchSync.Adopted ->
                                                "Signed in. Your history was restored from the cloud."
                                            is com.mreddy.liftz.data.sync.LaunchSync.BackedUp ->
                                                "Signed in and backed up."
                                            is com.mreddy.liftz.data.sync.LaunchSync.RemoteIsNewer ->
                                                "Signed in. There's newer data in the cloud — " +
                                                    "use Restore below if you want it."
                                            is com.mreddy.liftz.data.sync.LaunchSync.Offline ->
                                                "Signed in. You're offline, so nothing synced yet."
                                            is com.mreddy.liftz.data.sync.LaunchSync.Failed ->
                                                s.message
                                            else -> "Signed in."
                                        }
                                    }
                                    // Backing out of the picker is a choice, not an error.
                                    is SignInOutcome.Cancelled -> message = null
                                    is SignInOutcome.Failed -> message = r.message
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Signing in…" else "Sign in with Google") }
                }

                is AuthState.SignedIn -> {
                    Text(
                        who.email ?: who.displayName ?: "Signed in",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sync to the cloud", fontSize = 14.sp)
                            Text(
                                if (cloudOn) "Backups go to your Google account."
                                else "Paused. Backups go to your chosen folder instead.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = cloudOn,
                            onCheckedChange = { on ->
                                scope.launch { LiftzApp.syncPrefs().setCloudSyncEnabled(on) }
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    if (!confirmSignOut && !confirmDelete) {
                        Row {
                            TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("Delete cloud copy")
                            }
                        }
                    } else if (confirmDelete) {
                        Text(
                            "This erases the backup stored in your Google account. Everything " +
                                "on this phone stays exactly as it is — but any other device " +
                                "that hasn't synced yet will find nothing to restore.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            TextButton(
                                enabled = !busy,
                                onClick = { confirmDelete = false }
                            ) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        message = LiftzApp.sync().deleteCloudCopy().fold(
                                            onSuccess = { "Cloud copy deleted." },
                                            onFailure = { it.message ?: "Couldn't delete it." }
                                        )
                                        busy = false
                                        confirmDelete = false
                                    }
                                }
                            ) { Text(if (busy) "Deleting…" else "Delete it") }
                        }
                    } else {
                        Text(
                            "Signing out keeps everything on this phone — nothing is deleted " +
                                "here, and your cloud copy stays where it is. It just stops " +
                                "syncing until you sign back in.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                confirmSignOut = false
                                LiftzApp.auth().signOut()
                                message = "Signed out. Your data is still on this phone."
                            }) { Text("Sign out") }
                        }
                    }
                }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Credential Manager renders system UI over an Activity and rejects an application context, but
 * `LocalContext` inside a Compose tree can be a wrapper. Unwrap to the real thing.
 */
internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
