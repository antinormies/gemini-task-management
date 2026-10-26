package dev.antinormies.gtm.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antinormies.gtm.MainViewModel
import dev.antinormies.gtm.UiState
import dev.antinormies.gtm.VoiceState
import dev.antinormies.gtm.data.Preview
import dev.antinormies.gtm.ui.theme.GtmColors
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SpeakScreen(state: UiState, viewModel: MainViewModel, onOrb: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "speech")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "spin"
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "wave"
    )

    var typeOpen by remember { mutableStateOf(false) }
    var typedCommand by remember { mutableStateOf("") }
    val listening = state.voiceState == VoiceState.LISTENING
    val captured = state.voiceState == VoiceState.CAPTURED
    val error = state.voiceState == VoiceState.ERROR
    val thinking = state.voiceState == VoiceState.THINKING
    val headline = when (state.voiceState) {
        VoiceState.IDLE -> "Tap to speak"
        VoiceState.CONNECTING -> "Opening session"
        VoiceState.LISTENING -> "Listening"
        VoiceState.THINKING -> "Reading the request"
        VoiceState.CAPTURED -> "Task captured"
        VoiceState.ERROR -> "Needs attention"
    }
    val body = when {
        listening -> state.transcript.ifBlank { "Speak naturally. Tap the orb when you are done." }
        thinking -> "Building a structured proposal…"
        captured -> state.proposal?.message?.ifBlank { "Review what changed, then apply." } ?: "Review what changed, then apply."
        error -> state.error ?: "Nothing was saved. Try again."
        else -> "Say what you need to do. Call Task AI turns it into a task and shows you exactly what will change."
    }
    val pill = when (state.voiceState) {
        VoiceState.IDLE -> "Idle"
        VoiceState.CONNECTING -> "Connecting"
        VoiceState.LISTENING -> "Listening"
        VoiceState.THINKING -> "Thinking"
        VoiceState.CAPTURED -> "Captured"
        VoiceState.ERROR -> "Attention"
    }
    val pillColor = when (state.voiceState) {
        VoiceState.LISTENING, VoiceState.CONNECTING -> GtmColors.wave
        VoiceState.CAPTURED -> GtmColors.ok
        VoiceState.ERROR -> GtmColors.alert
        else -> GtmColors.stageMuted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GtmColors.stageTop, GtmColors.stage)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("CALL TASK AI", style = MaterialTheme.typography.labelLarge, color = GtmColors.stageMuted)
            Spacer(Modifier.weight(1f))
            Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(99.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(pillColor, RoundedCornerShape(99.dp)))
                    Spacer(Modifier.size(6.dp))
                    Text(pill, style = MaterialTheme.typography.labelMedium, color = GtmColors.stageMuted)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            headline,
            style = MaterialTheme.typography.headlineLarge,
            color = GtmColors.stageText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (listening) GtmColors.stageText else GtmColors.stageMuted,
            textAlign = TextAlign.Center,
            maxLines = if (listening) 4 else 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(20.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(226.dp)
                    .clickable(onClick = onOrb)
            ) {
                val c = center
                val base = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GtmColors.wave.copy(alpha = if (listening) 0.28f else 0.12f), Color.Transparent),
                        center = c,
                        radius = base
                    ),
                    radius = base * (if (listening) pulse else 0.9f),
                    center = c
                )
                drawCircle(Color.White.copy(alpha = 0.16f), radius = base - 8f, center = c, style = Stroke(1.dp.toPx()))
                rotate(spin) {
                    drawCircle(
                        color = GtmColors.wave.copy(alpha = if (listening || state.voiceState == VoiceState.CONNECTING) 0.55f else 0.25f),
                        radius = base - 34f,
                        center = c,
                        style = Stroke(1.dp.toPx())
                    )
                }
                drawCircle(GtmColors.stageCard, radius = base * 0.4f, center = c)
                drawCircle(
                    color = if (captured) GtmColors.accent else Color.White.copy(alpha = 0.18f),
                    radius = base * 0.4f,
                    center = c,
                    style = Stroke(1.dp.toPx())
                )
                val core = c
                if (captured) {
                    drawLine(Color.White, Offset(core.x - 12.dp.toPx(), core.y), Offset(core.x - 3.dp.toPx(), core.y + 10.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, Offset(core.x - 3.dp.toPx(), core.y + 10.dp.toPx()), Offset(core.x + 15.dp.toPx(), core.y - 12.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                } else if (error) {
                    drawLine(GtmColors.alert, Offset(core.x, core.y - 14.dp.toPx()), Offset(core.x, core.y + 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                    drawCircle(GtmColors.alert, 2.5.dp.toPx(), Offset(core.x, core.y + 15.dp.toPx()))
                } else {
                    val w = 16.dp.toPx()
                    val h = 28.dp.toPx()
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(core.x - w / 2, core.y - h / 2 - 2.dp.toPx()),
                        size = Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(core.x - 13.dp.toPx(), core.y - 5.dp.toPx()),
                        size = Size(26.dp.toPx(), 22.dp.toPx()),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawLine(Color.White, Offset(core.x, core.y + 17.dp.toPx()), Offset(core.x, core.y + 23.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            if (!listening) return@Canvas
            val count = 40
            val gap = size.width / count
            for (i in 0 until count) {
                val phase = wavePhase + i * 0.42f
                val amplitude = (0.2f + 0.8f * kotlin.math.abs(sin(phase))).coerceIn(0.2f, 1f)
                val h = size.height * amplitude
                drawLine(
                    color = GtmColors.wave.copy(alpha = 0.85f),
                    start = Offset(i * gap + gap / 2, size.height / 2 - h / 2),
                    end = Offset(i * gap + gap / 2, size.height / 2 + h / 2),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.proposal != null && !state.showProposal -> CaptureCard(state, viewModel)
            thinking -> Text(
                "AI is building the proposal…",
                style = MaterialTheme.typography.bodySmall,
                color = GtmColors.stageMuted
            )
            error -> ErrorCard(state.error ?: "Nothing was saved.", onOrb)
            captured -> Unit
            else -> Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Try saying: “Add a bug for the waveform freezing on Android.”",
                    style = MaterialTheme.typography.bodySmall,
                    color = GtmColors.stageMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { typeOpen = true }, enabled = !thinking && !listening) {
            Text("Type instead", color = GtmColors.wave, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                listening -> "Tap the orb when you are done · 10s silence auto-finishes"
                captured -> "Review the card, then add it to your tasks"
                error -> "Nothing was saved — the orb is still ready"
                else -> "Audio is handled by your phone's speech service. Call Task AI receives only the transcript."
            },
            style = MaterialTheme.typography.bodySmall,
            color = GtmColors.stageMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
    if (typeOpen) {
        AlertDialog(
            onDismissRequest = { typeOpen = false },
            title = { Text("Type a command") },
            text = {
                OutlinedTextField(
                    value = typedCommand,
                    onValueChange = { typedCommand = it },
                    placeholder = { Text("e.g. Add a task to buy milk tomorrow") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = typedCommand.trim()
                    if (text.isNotEmpty()) {
                        typeOpen = false
                        typedCommand = ""
                        viewModel.voiceFinished(text, 0)
                    }
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { typeOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CaptureCard(state: UiState, viewModel: MainViewModel) {
    val proposal = state.proposal ?: return
    val first: Preview? = proposal.preview.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GtmColors.stageCard, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            first?.title ?: "Proposed task",
            style = MaterialTheme.typography.titleMedium,
            color = GtmColors.stageText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Inbox", GtmColors.stageMuted)
            Chip("Proposal", GtmColors.stageMuted)
            first?.op?.let { Chip(it, GtmColors.stageMuted) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.applyProposal() },
                colors = ButtonDefaults.buttonColors(containerColor = GtmColors.accent),
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Add to tasks") }
            TextButton(onClick = { viewModel.rejectProposal() }) { Text("Discard", color = GtmColors.stageMuted) }
        }
        if (proposal.requiresConfirmation || proposal.intent != "create_task") {
            TextButton(onClick = { viewModel.reviewProposal() }, modifier = Modifier.align(Alignment.Start)) {
                Text("Review changes", color = GtmColors.wave)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = GtmColors.stageMuted)
        TextButton(onClick = onRetry) { Text("Try again", color = GtmColors.wave) }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(99.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
