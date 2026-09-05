package com.mreddy.liftz.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.auth.SignInOutcome
import com.mreddy.liftz.ui.settings.findActivity
import kotlinx.coroutines.launch

private enum class Method { GOOGLE, EMAIL, PHONE }

/**
 * Sign in or create an account, by whichever route somebody prefers.
 *
 * Three providers rather than one because a Google account is not universal, and because handing
 * a Google identity to a hobby app is a bigger ask than an email address. Which provider proves
 * the identity changes nothing downstream: sync keys off the uid, and every provider produces one.
 */
@Composable
fun SignInScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var method by remember { mutableStateOf(Method.GOOGLE) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    var phone by remember { mutableStateOf("+91") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }

    fun finish(outcome: SignInOutcome) {
        busy = false
        when (outcome) {
            is SignInOutcome.Success -> {
                // Sync straight away so the account is not an empty promise on first use.
                scope.launch { LiftzApp.sync().syncOnLaunch(); onDone() }
            }
            is SignInOutcome.Cancelled -> message = null
            is SignInOutcome.Failed -> message = outcome.message
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your training history follows your account to a new phone. The app works fine " +
                "without one — this only adds sync.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Method.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = method == m,
                    onClick = { method = m; message = null },
                    shape = SegmentedButtonDefaults.itemShape(i, Method.entries.size)
                ) {
                    Text(
                        when (m) {
                            Method.GOOGLE -> "Google"
                            Method.EMAIL -> "Email"
                            Method.PHONE -> "Phone"
                        },
                        fontSize = 13.sp
                    )
                }
            }
        }

        when (method) {
            Method.GOOGLE -> {
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val activity = context.findActivity() ?: return@Button
                        busy = true; message = null
                        scope.launch { finish(LiftzApp.auth().signIn(activity)) }
                    }
                ) { Text(if (busy) "Signing in…" else "Continue with Google") }
            }

            Method.EMAIL -> {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email, imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !busy && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true; message = null
                        scope.launch {
                            finish(
                                if (creating) LiftzApp.auth().signUpWithEmail(email, password)
                                else LiftzApp.auth().signInWithEmail(email, password)
                            )
                        }
                    }
                ) {
                    Text(
                        when {
                            busy -> "Working…"
                            creating -> "Create account"
                            else -> "Sign in"
                        }
                    )
                }
                Row {
                    TextButton(onClick = { creating = !creating; message = null }) {
                        Text(if (creating) "I already have an account" else "Create an account")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = email.isNotBlank(),
                        onClick = {
                            scope.launch {
                                message = LiftzApp.auth().sendPasswordReset(email).fold(
                                    onSuccess = { "Reset email sent to $email." },
                                    onFailure = { it.message ?: "Couldn't send that." }
                                )
                            }
                        }
                    ) { Text("Reset password") }
                }
                if (password.isNotEmpty() && password.length < 6) {
                    Text(
                        "Passwords need at least six characters.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Method.PHONE -> {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    supportingText = { Text("Include the country code, e.g. +91…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                if (verificationId == null) {
                    Button(
                        enabled = !busy && phone.length > 8,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val activity = context.findActivity() ?: return@Button
                            busy = true; message = null
                            LiftzApp.auth().startPhoneVerification(
                                activity = activity,
                                phoneE164 = phone.filter { it.isDigit() || it == '+' },
                                onCodeSent = { id -> busy = false; verificationId = id },
                                // Play Integrity can verify with no SMS at all, so this path has
                                // to exist or the screen would wait forever for a code that is
                                // never going to arrive.
                                onVerified = { finish(SignInOutcome.Success) },
                                onError = { busy = false; message = it }
                            )
                        }
                    ) { Text(if (busy) "Sending…" else "Send code") }
                } else {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("6-digit code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !busy && code.length >= 6,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true
                            scope.launch {
                                finish(LiftzApp.auth().confirmPhoneCode(verificationId!!, code))
                            }
                        }
                    ) { Text(if (busy) "Checking…" else "Verify") }
                    TextButton(onClick = { verificationId = null; code = "" }) {
                        Text("Use a different number")
                    }
                }
            }
        }

        message?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Not now — keep using it offline")
        }
        Text(
            "What signing in sends off your phone is described at " +
                "mreddyliftz.firebaseapp.com/privacy.html",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
