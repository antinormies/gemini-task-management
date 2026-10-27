package id.ai.antaratask.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import id.ai.antaratask.MainViewModel
import id.ai.antaratask.UiState
import id.ai.antaratask.VoiceState
import id.ai.antaratask.data.Preview
import id.ai.antaratask.ui.components.VoiceOrb
import id.ai.antaratask.ui.theme.GtmColors

private val SpeakSerif = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 38.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.4).sp
)

@Composable
fun SpeakScreen(state: UiState, viewModel: MainViewModel, onOrb: () -> Unit) {
    var typeOpen by remember { mutableStateOf(false) }
    var typedCommand by remember { mutableStateOf("") }

    val listening = state.voiceState == VoiceState.LISTENING
    val captured = state.voiceState == VoiceState.CAPTURED
    val error = state.voiceState == VoiceState.ERROR
    val thinking = state.voiceState == VoiceState.THINKING
    val proposal = state.proposal
    val showCapture = proposal != null && !state.showProposal

    val pill = when (state.voiceState) {
        VoiceState.IDLE -> "Idle"
        VoiceState.CONNECTING -> "Connecting"
        VoiceState.LISTENING -> "Listening"
        VoiceState.THINKING -> "Thinking"
        VoiceState.CAPTURED -> "Captured"
        VoiceState.ERROR -> "Attention"
    }
    val pillDot by animateColorAsState(
        targetValue = when (state.voiceState) {
            VoiceState.LISTENING, VoiceState.CONNECTING -> GtmColors.orbViolet
            VoiceState.CAPTURED -> Color(0xFF3FA37E)
            VoiceState.ERROR -> Color(0xFFD24A6A)
            else -> GtmColors.speakMuted.copy(alpha = 0.55f)
        },
        animationSpec = tween(400),
        label = "pillDot"
    )
    val caption = when {
        error -> ""
        listening -> "Listening… tap the orb when you're done."
        thinking -> "Reading the request…"
        captured -> proposal?.message?.takeIf { it.isNotBlank() } ?: "Review the card below."
        else -> "Tap the orb and speak naturally."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        GtmColors.speakBgTop,
                        GtmColors.speakBg,
                        GtmColors.speakBgBottom
                    )
                )
            )
    ) {
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
                .padding(top = 18.dp, bottom = 132.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CALL TASK AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.2.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = GtmColors.speakMuted
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = Color.White.copy(alpha = 0.6f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).background(pillDot, CircleShape))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            pill,
                            style = MaterialTheme.typography.labelSmall,
                            color = GtmColors.speakMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            VoiceOrb(
                state = state.voiceState,
                onClick = onOrb,
                modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f)
            )
            Spacer(Modifier.height(14.dp))

            Text(
                "Speak your ideas\nout loud",
                style = SpeakSerif,
                color = GtmColors.speakInk,
                textAlign = TextAlign.Center
            )
            if (caption.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GtmColors.speakMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            if (listening && state.transcript.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GtmColors.speakInk,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showCapture) {
                Spacer(Modifier.height(20.dp))
                CaptureCard(state, viewModel)
            } else if (error) {
                Spacer(Modifier.height(20.dp))
                ErrorCard(state.error ?: "Nothing was saved.", onOrb)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WritePill(enabled = !thinking && !listening) { typeOpen = true }
            Spacer(Modifier.height(8.dp))
            Text(
                "Audio stays on your phone. Only the transcript is sent.",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                ),
                color = GtmColors.speakMuted.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }

    if (typeOpen) {
        Dialog(onDismissRequest = { typeOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFFFDFBFF),
                border = BorderStroke(1.dp, Color.White),
                shadowElevation = 24.dp
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        "Write instead",
                        style = MaterialTheme.typography.titleLarge,
                        color = GtmColors.speakInk
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = typedCommand,
                        onValueChange = { typedCommand = it },
                        placeholder = {
                            Text(
                                "e.g. Add a task to call the vet tomorrow",
                                color = GtmColors.speakMuted.copy(alpha = 0.75f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = GtmColors.speakInk
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GtmColors.speakAccent,
                            unfocusedBorderColor = GtmColors.speakLine,
                            cursorColor = GtmColors.speakAccent,
                            focusedTextColor = GtmColors.speakInk,
                            unfocusedTextColor = GtmColors.speakInk
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { typeOpen = false }) {
                            Text("Cancel", color = GtmColors.speakMuted)
                        }
                        Button(
                            onClick = {
                                val text = typedCommand.trim()
                                if (text.isNotEmpty()) {
                                    typeOpen = false
                                    typedCommand = ""
                                    viewModel.voiceFinished(text, 0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GtmColors.speakAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(99.dp)
                        ) { Text("Send") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientGlow() {
    Canvas(Modifier.fillMaxSize()) {
        val pinkCenter = Offset(size.width * 0.5f, size.height * 0.32f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GtmColors.orbPink.copy(alpha = 0.2f), Color.Transparent),
                center = pinkCenter,
                radius = size.width * 0.72f
            ),
            radius = size.width * 0.72f,
            center = pinkCenter
        )
        val blueCenter = Offset(size.width * 0.12f, size.height * 0.6f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GtmColors.orbBlue.copy(alpha = 0.14f), Color.Transparent),
                center = blueCenter,
                radius = size.width * 0.55f
            ),
            radius = size.width * 0.55f,
            center = blueCenter
        )
    }
}

@Composable
private fun WritePill(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        color = Color.White.copy(alpha = if (enabled) 0.8f else 0.55f),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Write instead…",
                style = MaterialTheme.typography.bodyMedium,
                color = GtmColors.speakMuted,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(GtmColors.speakAccentSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Write a command",
                    tint = GtmColors.speakAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(state: UiState, viewModel: MainViewModel) {
    val proposal = state.proposal ?: return
    val first: Preview? = proposal.preview.firstOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.94f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 14.dp
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    first?.title ?: "Proposed task",
                    style = MaterialTheme.typography.titleMedium,
                    color = GtmColors.speakInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (first != null) {
                    Text(
                        first.op,
                        style = MaterialTheme.typography.labelSmall,
                        color = GtmColors.speakAccent,
                        modifier = Modifier
                            .background(GtmColors.speakAccentSoft, RoundedCornerShape(99.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            first?.summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = GtmColors.speakMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.applyProposal() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GtmColors.speakAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (proposal.intent == "create_task") "Add to tasks" else "Apply changes")
                }
                TextButton(onClick = { viewModel.rejectProposal() }) {
                    Text("Discard", color = GtmColors.speakMuted)
                }
            }
            if (proposal.requiresConfirmation || proposal.intent != "create_task") {
                TextButton(
                    onClick = { viewModel.reviewProposal() },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("Review changes", color = GtmColors.speakAccent)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFF2F6).copy(alpha = 0.95f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFFFFD8E3))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A3B52),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onRetry) {
                Text("Try again", color = GtmColors.speakAccent)
            }
        }
    }
}
