package id.ai.antaratask.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.ai.antaratask.MainViewModel
import id.ai.antaratask.UiState
import id.ai.antaratask.VoiceState
import id.ai.antaratask.ui.components.GhostButton
import id.ai.antaratask.ui.components.LanguagePill
import id.ai.antaratask.ui.components.PrimaryButton
import id.ai.antaratask.ui.components.SecondaryButton
import id.ai.antaratask.ui.components.StackCard
import id.ai.antaratask.ui.components.StateOrb
import id.ai.antaratask.ui.theme.NumoTheme
import java.util.Locale

/**
 * Voice capture surface.
 *
 * Composition follows the reference measurements: headline and tip at the top, the disc
 * held in the flexible middle so it lands around 60% of the height, the language pill
 * directly beneath it, the result card under that, and one line of guidance at the
 * bottom. Nothing else competes with the disc.
 */
@Composable
fun SpeakScreen(state: UiState, viewModel: MainViewModel, onOrb: () -> Unit) {
    val c = NumoTheme.colors
    val t = NumoTheme.type
    val space = NumoTheme.space
    var showTypeDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .statusBarsPadding()
            .padding(horizontal = space.gutter)
    ) {
        Spacer(Modifier.height(space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state.voiceState == VoiceState.LISTENING || state.voiceState == VoiceState.THINKING) {
                AiBadge()
            }
        }
        Text(
            text = "Braindump tasks\nwith your voice",
            style = t.displayL,
            color = c.ink
        )
        Spacer(Modifier.height(space.sm))
        Text(
            text = when (state.voiceState) {
                VoiceState.IDLE -> "Tip: mention the time, or say “every day” for a routine."
                VoiceState.CONNECTING -> "Warming up the microphone…"
                VoiceState.LISTENING -> "Speak freely. Tap the disc when you are done."
                VoiceState.THINKING -> "Turning your words into tasks."
                VoiceState.CAPTURED -> "Say more, or confirm below."
                VoiceState.ERROR -> "Nothing was saved. The disc is still ready."
            },
            style = t.bodyM,
            color = c.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Flexible space above the disc. The 1 : 0.35 split puts the disc centre near 57%
        // of the usable height, which is where the reference holds it.
        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stateLabel(state.voiceState),
                style = t.label,
                color = stateColor(state.voiceState)
            )
            Spacer(Modifier.height(space.md))
            StateOrb(state = state.voiceState, onClick = onOrb)
            Spacer(Modifier.height(space.lg))
            LanguagePill(Locale.getDefault().displayLanguage)
        }

        Spacer(Modifier.weight(0.35f))

        when (state.voiceState) {
            VoiceState.ERROR -> ErrorBlock(
                message = state.error ?: "The microphone stopped unexpectedly.",
                onRetry = onOrb
            )
            VoiceState.LISTENING -> if (state.transcript.isNotBlank()) TranscriptCard(state.transcript)
            VoiceState.THINKING -> SkeletonCard()
            VoiceState.CAPTURED -> CaptureCard(state, viewModel)
            VoiceState.IDLE, VoiceState.CONNECTING -> Unit
        }

        Spacer(Modifier.height(space.md))
        Text(
            text = voiceHint(state.voiceState),
            style = t.meta,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .heightIn(min = NumoTheme.size.minTouch)
                    .clip(NumoTheme.radius.pill)
                    .clickable { showTypeDialog = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Type instead", style = t.label, color = c.muted)
            }
        }
    }

    if (showTypeDialog) {
        TypeInsteadDialog(
            onDismiss = { showTypeDialog = false },
            onSubmit = { text ->
                showTypeDialog = false
                viewModel.voiceFinished(text, 0)
            }
        )
    }
}

@Composable
private fun AiBadge() {
    val c = NumoTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = c.success,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Ai", style = NumoTheme.type.label, color = c.successStrong)
    }
}

@Composable
private fun stateLabel(state: VoiceState): String = when (state) {
    VoiceState.IDLE -> "Hold and speak"
    VoiceState.CONNECTING -> "Connecting"
    VoiceState.LISTENING -> "Listening"
    VoiceState.THINKING -> "Your tasks incoming…"
    VoiceState.CAPTURED -> "Captured"
    VoiceState.ERROR -> "Needs attention"
}

@Composable
private fun stateColor(state: VoiceState) = when (state) {
    VoiceState.IDLE -> NumoTheme.colors.warning
    VoiceState.CONNECTING -> NumoTheme.colors.brandStrong
    VoiceState.LISTENING, VoiceState.ERROR -> NumoTheme.colors.danger
    VoiceState.THINKING, VoiceState.CAPTURED -> NumoTheme.colors.successStrong
}

private fun voiceHint(state: VoiceState): String = when (state) {
    VoiceState.IDLE -> "Tap the disc to start"
    VoiceState.LISTENING -> "Tap when you are done"
    VoiceState.THINKING -> "One moment"
    VoiceState.CAPTURED -> "Nothing is saved until you confirm"
    VoiceState.ERROR -> "Nothing was saved — try again or type it"
    VoiceState.CONNECTING -> "Getting ready"
}

@Composable
private fun TranscriptCard(text: String) {
    val c = NumoTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(NumoTheme.radius.card)
            .background(c.surfaceWarm)
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = NumoTheme.type.bodyL,
            color = c.inkBody,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SkeletonCard() {
    val c = NumoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NumoTheme.radius.card)
            .background(c.surfaceWarm.copy(alpha = 0.45f))
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.surfaceWarm)
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.surface)
        )
    }
}

@Composable
private fun CaptureCard(state: UiState, viewModel: MainViewModel) {
    val c = NumoTheme.colors
    val t = NumoTheme.type
    val haptics = LocalHapticFeedback.current
    val proposal = state.proposal
    val title = proposal?.preview?.firstOrNull()?.title
        ?: state.transcript.take(60).ifBlank { "Captured task" }
    val summaries = proposal?.preview?.map { it.summary }.orEmpty()
    val needsReview = proposal != null && (
        proposal.preview.any { it.op != "create" } || proposal.confidence < 0.6
        )

    StackCard(color = c.surface) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = t.titleM, color = c.ink, maxLines = 2)
            if (summaries.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    summaries.joinToString("  ·  "),
                    style = t.meta,
                    color = c.muted,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                label = if (needsReview) "Review and add" else "Add to your tasks",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (needsReview) viewModel.reviewProposal() else viewModel.applyProposal()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Discard", viewModel::rejectProposal)
                Spacer(Modifier.weight(1f))
                GhostButton(
                    label = "New braindump",
                    onClick = { viewModel.resetVoice() }
                )
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val c = NumoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NumoTheme.radius.row)
            .background(c.dangerSoft)
            .padding(16.dp)
    ) {
        Text(message, style = NumoTheme.type.bodyM, color = c.dangerStrong)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Try again", onRetry, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TypeInsteadDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = NumoTheme.radius.card,
        containerColor = NumoTheme.colors.surface,
        title = { Text("Type the task", style = NumoTheme.type.displayM, color = NumoTheme.colors.ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Move the report to Friday and make it urgent") },
                shape = NumoTheme.radius.control,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onSubmit(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text("Interpret", style = NumoTheme.type.titleM, color = NumoTheme.colors.brandStrong)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = NumoTheme.type.titleM, color = NumoTheme.colors.muted)
            }
        }
    )
}
