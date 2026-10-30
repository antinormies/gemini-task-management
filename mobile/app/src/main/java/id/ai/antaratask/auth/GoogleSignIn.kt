package id.ai.antaratask.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import id.ai.antaratask.BuildConfig

class GoogleSignIn(private val activity: Activity) {
    suspend fun idToken(): String {
        val clientId = BuildConfig.GTM_GOOGLE_CLIENT_ID
        check(clientId.isNotBlank()) { "Google sign-in is not configured. Add GTM_GOOGLE_CLIENT_ID." }
        val manager = CredentialManager.create(activity)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credential = manager.getCredential(activity, request).credential
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Google did not return an ID token.")
    }
}
