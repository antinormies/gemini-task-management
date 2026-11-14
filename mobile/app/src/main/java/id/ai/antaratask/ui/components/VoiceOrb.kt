package id.ai.antaratask.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.ai.antaratask.VoiceState
import id.ai.antaratask.ui.theme.NumoTheme

/**
 * The voice state circle. Idle orange, listening red with expanding rings, thinking
 * green with three bouncing dots, captured green check, error red alert. The rings and
 * dots are the app's only decorative loops and are frozen under reduced motion.
 */
@Composable
fun StateOrb(
    state: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    val reduced = NumoTheme.reducedMotion
    val targetFill = when (state) {
        VoiceState.IDLE -> c.warning
        VoiceState.CONNECTING -> c.brand
        VoiceState.LISTENING -> c.danger
        VoiceState.THINKING -> c.success
        VoiceState.CAPTURED -> c.success
        VoiceState.ERROR -> c.danger
    }
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(NumoTheme.motion.quick),
        label = "orbFill"
    )
    val glyphTint = when (state) {
        VoiceState.CAPTURED -> c.onSuccess
        else -> Color.White
    }
    val label = when (state) {
        VoiceState.IDLE -> "Start voice capture"
        VoiceState.CONNECTING -> "Connecting"
        VoiceState.LISTENING -> "Listening. Tap to finish."
        VoiceState.THINKING -> "Building your task"
        VoiceState.CAPTURED -> "Task captured"
        VoiceState.ERROR -> "Something went wrong. Tap to retry."
    }
    val showRings = state == VoiceState.LISTENING
    val ringPhase = if (showRings) recordingPhase(enabled = !reduced) else 0f

    Box(
        modifier
            .size(NumoTheme.size.orb)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (showRings) {
            Canvas(Modifier.size(NumoTheme.size.orb)) {
                val maxRadius = size.minDimension / 2f
                repeat(3) { index ->
                    val phase = (ringPhase + index / 3f) % 1f
                    drawCircle(
                        color = c.danger.copy(alpha = 0.30f * (1f - phase)),
                        radius = maxRadius * (0.66f + 0.34f * phase),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }
        Box(
            Modifier
                .size(NumoTheme.size.orbCore)
                .clip(CircleShape)
                .background(fill)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                VoiceState.THINKING -> ThinkingDots(tint = Color.White, animated = !reduced)
                VoiceState.LISTENING -> Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                )
                VoiceState.CAPTURED -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = glyphTint,
                    modifier = Modifier.size(NumoTheme.size.orbGlyph * 0.8f)
                )
                VoiceState.ERROR -> Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = glyphTint,
                    modifier = Modifier.size(NumoTheme.size.orbGlyph * 0.8f)
                )
                else -> Icon(
                    Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = glyphTint,
                    modifier = Modifier.size(NumoTheme.size.orbGlyph * 0.8f)
                )
            }
        }
    }
}

@Composable
private fun recordingPhase(enabled: Boolean): Float {
    if (!enabled) return 0.5f
    val transition = rememberInfiniteTransition(label = "recording")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(NumoTheme.motion.pulse, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    return phase
}

@Composable
private fun ThinkingDots(tint: Color, animated: Boolean) {
    val transition = if (animated) rememberInfiniteTransition(label = "thinking") else null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val lift = if (transition == null) {
                0f
            } else {
                val animatedLift by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(NumoTheme.motion.dots, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 140)
                    ),
                    label = "dot$index"
                )
                animatedLift
            }
            Spacer(
                Modifier
                    .size(9.dp)
                    .offset(y = (lift * 4f).dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

/** Outlined language pill shown under the orb. */
@Composable
fun LanguagePill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(40.dp)
            .clip(NumoTheme.radius.pill)
            .border(1.5.dp, NumoTheme.colors.lineStrong, NumoTheme.radius.pill)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = NumoTheme.type.meta, color = NumoTheme.colors.inkBody)
    }
}
