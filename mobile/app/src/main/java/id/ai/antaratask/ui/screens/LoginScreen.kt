package id.ai.antaratask.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.ai.antaratask.BuildConfig
import id.ai.antaratask.UiState
import id.ai.antaratask.auth.GoogleSignIn
import id.ai.antaratask.ui.theme.GtmColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(state: UiState, onToken: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val configured = BuildConfig.GTM_GOOGLE_CLIENT_ID.isNotBlank()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        if (!state.error.isNullOrBlank()) error = state.error
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GtmColors.speakBgTop, GtmColors.speakBg, GtmColors.speakBgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "ANTARA TASK AI",
                style = MaterialTheme.typography.labelLarge,
                color = GtmColors.speakMuted,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .size(148.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.White,
                                Color(0xFFF0E7FF),
                                Color(0xFFDCD6FF),
                                Color(0xFFC9D8FF)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "Sign in to\nyour tasks",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif,
                color = Color(0xFF2D2548),
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Use the same Google account on web and Android. Your projects, tasks, and AI history stay scoped to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = GtmColors.speakMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = {
                    if (activity == null) {
                        error = "Google sign-in needs an Activity context."
                        return@Button
                    }
                    error = null
                    busy = true
                    scope.launch {
                        runCatching { GoogleSignIn(activity).idToken() }
                            .onSuccess(onToken)
                            .onFailure { error = it.message ?: "Google sign-in failed." }
                        busy = false
                    }
                },
                enabled = configured && !busy && !state.loading,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    disabledContainerColor = Color.White.copy(alpha = 0.55f),
                    disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(54.dp)
            ) {
                if (busy || state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.size(10.dp))
                    Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (!configured) {
                Text(
                    "Google sign-in is not configured. Add GTM_GOOGLE_CLIENT_ID to the build and server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A6A00),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            } else if (error != null) {
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9B2C4A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Google verifies your identity. Antara Task AI never sees your password.",
                style = MaterialTheme.typography.labelSmall,
                color = GtmColors.speakMuted.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }
    }
}
