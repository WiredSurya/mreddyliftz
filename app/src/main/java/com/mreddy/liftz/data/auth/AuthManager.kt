package com.mreddy.liftz.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mreddy.liftz.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Who is signed in, if anyone. */
sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(
        val uid: String,
        val email: String?,
        val displayName: String?
    ) : AuthState
}

/**
 * Google sign-in, and nothing else.
 *
 * Sign-in exists here for exactly one reason: to give cloud sync a stable per-person key, so a
 * new phone can pull down the same training history. It is NOT a gate on the app. Every screen
 * works signed out, the database is local either way, and signing out does not delete anything
 * from the device. That is a deliberate constraint — an account should buy you something, not
 * cost you access to your own data.
 *
 * WHY CREDENTIAL MANAGER: the older `GoogleSignInClient` / `GoogleSignInOptions` API that most
 * tutorials still show is deprecated. Credential Manager is the supported path, and pairing it
 * with `credentials-play-services-auth` is what makes it work below API 34 — which is every
 * device this app targets, including the Android 9 phone it is tested on. Without that artifact
 * the request finds no providers and fails with a confusing "no credentials" error.
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * The OAuth web client id, NOT the Android one.
     *
     * This string is generated into resources by the google-services plugin from
     * `google-services.json`, so it is never hardcoded and never drifts from the Firebase
     * project. If it fails to resolve, the JSON is missing its `client_type: 3` block — which
     * means the SHA-1 fingerprints or the Google provider were not set up before it was
     * downloaded. Re-download it from the Firebase console rather than patching around it.
     */
    private val webClientId: String get() = context.getString(R.string.default_web_client_id)

    /** Emits on every sign-in and sign-out, including the restored session at app start. */
    fun state(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toState()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /** Current state without collecting, for one-shot checks like "should I sync on launch". */
    fun currentState(): AuthState = auth.currentUser.toState()

    val isSignedIn: Boolean get() = auth.currentUser != null

    /** Firestore document key. Null when signed out. */
    val uid: String? get() = auth.currentUser?.uid

    /**
     * Show the Google account picker and sign in.
     *
     * [activityContext] must be an Activity — Credential Manager renders system UI on top of it
     * and throws if handed an application context.
     *
     * A user backing out of the picker is NOT an error. It returns [SignInOutcome.Cancelled] so
     * the UI can go quiet instead of showing a failure they caused on purpose.
     */
    suspend fun signIn(activityContext: Context): SignInOutcome {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return SignInOutcome.Failed("Google returned a credential the app can't read.")
            }

            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            SignInOutcome.Success
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            // Almost always one of two setup problems rather than a real absence of accounts.
            SignInOutcome.Failed(
                "No Google account was offered. Check that a Google account is added to this " +
                    "phone, and that this app's signing fingerprint is registered in Firebase."
            )
        } catch (e: GetCredentialException) {
            SignInOutcome.Failed(e.message ?: "Sign-in failed.")
        } catch (e: Exception) {
            SignInOutcome.Failed(e.message ?: "Sign-in failed.")
        }
    }

    /**
     * Sign out of the cloud. Local data is untouched — this is not a wipe, and the app keeps
     * working exactly as it did before an account existed.
     */
    fun signOut() {
        auth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser?.toState(): AuthState =
        if (this == null) AuthState.SignedOut
        else AuthState.SignedIn(uid = uid, email = email, displayName = displayName)
}

/** Result of a sign-in attempt. Cancellation is separated from failure on purpose. */
sealed interface SignInOutcome {
    data object Success : SignInOutcome
    data object Cancelled : SignInOutcome
    data class Failed(val message: String) : SignInOutcome
}
