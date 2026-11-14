package id.ai.antaratask.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.ai.antaratask.BuildConfig
import id.ai.antaratask.UiState
import id.ai.antaratask.auth.GoogleSignIn
import id.ai.antaratask.ui.components.PrimaryButton
import id.ai.antaratask.ui.theme.NumoTheme
import kotlinx.coroutines.launch

/**
 * Google sign-in, split layout: saturated brand block on top, white credential panel
 * below. The sign-in mechanism is unchanged — only the surface is new.
 */
@Composable
fun LoginScreen(state: UiState, onToken: (String) -> Unit) {
    val c = NumoTheme.colors
    val t = NumoTheme.type
    val space = NumoTheme.space
    val activity = androidx.compose.ui.platform.LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    val configured = BuildConfig.GTM_GOOGLE_CLIENT_ID.isNotBlank()
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        if (!state.error.isNullOrBlank()) localError = state.error
    }

    fun signIn() {
        if (activity == null) {
            localError = "Google sign-in needs an Activity context."
            return
        }
        localError = null
        busy = true
        scope.launch {
            runCatching { GoogleSignIn(activity).idToken() }
                .onSuccess(onToken)
                .onFailure { localError = it.message ?: "Google sign-in failed." }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .verticalScroll(rememberScrollState())
    ) {
        // Brand block
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(c.brand)
                .statusBarsPadding()
                .padding(horizontal = space.gutter)
                .padding(top = space.xxl, bottom = space.xxl)
                .heightIn(min = 232.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.onBrand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = c.brand,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(space.xl))
            Text("AntaraTask", style = t.displayXL, color = c.onBrand)
            Spacer(Modifier.height(space.sm))
            Text(
                "Talk. Confirm. Done.",
                style = t.bodyM,
                color = c.onBrand.copy(alpha = 0.9f)
            )
        }

        // Credential panel
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = space.gutter)
                .padding(top = space.xl)
        ) {
            Text("Sign in to your tasks", style = t.displayM, color = c.ink)
            Spacer(Modifier.height(space.sm))
            Text(
                "Speak a task, review what the assistant understood, and confirm. "
                    + "Your projects and history stay scoped to this Google account.",
                style = t.bodyM,
                color = c.muted
            )
            Spacer(Modifier.height(space.xl))
            PrimaryButton(
                label = if (busy || state.loading) "Signing in…" else "Continue with Google",
                onClick = { signIn() },
                enabled = configured && !busy && !state.loading,
                loading = busy || state.loading,
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c.onBrand),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "G",
                            style = t.label,
                            color = c.brand,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            )
            if (!configured) {
                Spacer(Modifier.height(space.md))
                Text(
                    "Google sign-in is not configured for this build. Set GTM_GOOGLE_CLIENT_ID.",
                    style = t.meta,
                    color = c.warning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NumoTheme.radius.control)
                        .background(c.warningSoft)
                        .padding(12.dp)
                )
            }
            localError?.let { message ->
                Spacer(Modifier.height(space.md))
                Text(
                    message,
                    style = t.meta,
                    color = c.dangerStrong,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NumoTheme.radius.control)
                        .background(c.dangerSoft)
                        .padding(12.dp)
                )
            }
            Spacer(Modifier.height(space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(2.dp))
                Text(
                    "Audio is handled by your phone's speech service. "
                        + "Only the transcript is sent.",
                    style = t.meta,
                    color = c.mutedSoft
                )
            }
            Spacer(Modifier.height(space.huge))
        }
    }
}
