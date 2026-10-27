package id.ai.antaratask.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import id.ai.antaratask.VoiceState
import id.ai.antaratask.ui.theme.GtmColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun sinF(value: Float): Float = sin(value.toDouble()).toFloat()
private fun cosF(value: Float): Float = cos(value.toDouble()).toFloat()

/**
 * The luminous pastel sphere from the voice reference.
 *
 * Idle: a slow-breathing glass orb. Listening: the aurora accelerates, the
 * halo pulses, and rings expand. Thinking: a faster swirl plus a rotating arc.
 * Error: the palette and glow shift to rose, with a short shake. Tapping fires
 * a quick expanding burst before the voice state changes.
 */
@Composable
fun VoiceOrb(state: VoiceState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val listening = state == VoiceState.LISTENING
    val thinking = state == VoiceState.THINKING
    val captured = state == VoiceState.CAPTURED
    val error = state == VoiceState.ERROR
    val active = listening || thinking

    val activity by animateFloatAsState(
        targetValue = when {
            listening -> 1f
            thinking -> 0.35f
            else -> 0f
        },
        animationSpec = tween(500),
        label = "activity"
    )

    val targetPalette = when {
        error -> listOf(Color(0xFFFF7A93), Color(0xFFFF4D6D), Color(0xFFFFB3C1))
        captured -> listOf(GtmColors.orbMint, Color(0xFF8FD9FF), Color.White)
        else -> listOf(Color(0xFFFF8FC8), Color(0xFF7FB0FF), Color(0xFFA47BFF))
    }
    val colorA by animateColorAsState(targetPalette[0], tween(550), label = "colorA")
    val colorB by animateColorAsState(targetPalette[1], tween(550), label = "colorB")
    val colorC by animateColorAsState(targetPalette[2], tween(550), label = "colorC")
    val palette = listOf(colorA, colorB, colorC)
    val altPalette = when {
        error -> listOf(Color(0xFFFF6B81), Color(0xFFFFB3C1), Color(0xFFFF8FA3))
        captured -> listOf(Color(0xFF9FE8CE), Color(0xFF8FD9FF), Color.White)
        else -> listOf(Color(0xFF8FD8FF), Color(0xFFFF9AD5), Color(0xFF8F7BFF))
    }
    val colorIntensity = when {
        listening -> 0.85f
        thinking -> 0.42f
        else -> 0f
    }

    val glowTint by animateColorAsState(
        targetValue = when {
            error -> Color(0xFFFF7A93)
            captured -> Color(0xFF9FE8CE)
            else -> Color(0xFFFFD7EC)
        },
        animationSpec = tween(600),
        label = "glowTint"
    )
    val rimTint by animateColorAsState(
        targetValue = when {
            error -> Color(0xFFFF9FB0)
            captured -> Color(0xFFB7F0DA)
            else -> Color.White
        },
        animationSpec = tween(600),
        label = "rimTint"
    )

    val burst = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        if (state == VoiceState.ERROR) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(70))
            shake.animateTo(-1f, tween(70))
            shake.animateTo(1f, tween(70))
            shake.animateTo(-1f, tween(70))
            shake.animateTo(0f, tween(120))
        }
    }

    val transition = rememberInfiniteTransition(label = "voice-orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    listening -> 5200
                    thinking -> 14000
                    else -> 17000
                },
                easing = LinearEasing
            )
        ),
        label = "phase"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 12000 else 60000, easing = LinearEasing)
        ),
        label = "spin"
    )
    val breath by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            tween(4200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breath"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    listening -> 1300
                    error -> 900
                    else -> 2600
                },
                easing = LinearEasing
            )
        ),
        label = "pulse"
    )
    val zoom by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.085f else 1f,
        animationSpec = infiniteRepeatable(
            tween(650, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "zoom"
    )
    val colorCycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when {
                    listening -> 800
                    thinking -> 8000
                    else -> 12000
                },
                easing = LinearEasing
            ),
            RepeatMode.Reverse
        ),
        label = "colorCycle"
    )

    Box(
        modifier = modifier
            .graphicsLayer { translationX = shake.value * 14f }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
                scope.launch {
                    burst.snapTo(1f)
                    burst.animateTo(0f, tween(650, easing = FastOutSlowInEasing))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension / 2f
            val scale = when {
                listening -> zoom
                thinking -> 1f
                else -> breath
            }
            val r = base * 0.74f * scale
            val activePalette = palette.mapIndexed { index, color ->
                lerp(color, altPalette[index], colorIntensity * colorCycle)
            }

            val haloPulse = when {
                listening -> 1f + 0.06f * sinF(pulse * 2f * PI.toFloat())
                thinking -> 1f + 0.02f * sinF(phase * 0.8f)
                else -> 1f
            }
            val haloRadius = r * 1.5f * haloPulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        glowTint.copy(alpha = 0.32f + 0.18f * activity),
                        glowTint.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = c
            )
            if (error) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowTint.copy(alpha = 0.4f), Color.Transparent),
                        center = c,
                        radius = r * 1.35f
                    ),
                    radius = r * 1.35f,
                    center = c
                )
            }

            val tapBurst = burst.value
            if (tapBurst > 0.01f) {
                drawCircle(
                    color = Color.White.copy(alpha = tapBurst * 0.5f),
                    radius = r * (1f + (1f - tapBurst) * 0.45f),
                    center = c,
                    style = Stroke(2.dp.toPx())
                )
                drawCircle(
                    color = glowTint.copy(alpha = tapBurst * 0.3f),
                    radius = r * (0.9f + (1f - tapBurst) * 0.3f),
                    center = c,
                    style = Stroke(6.dp.toPx())
                )
            }

            if (listening) {
                repeat(2) { index ->
                    val progress = (pulse + index * 0.5f) % 1f
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - progress) * 0.55f),
                        radius = r * (1.02f + progress * 0.26f),
                        center = c,
                        style = Stroke(width = 1.3.dp.toPx())
                    )
                }
            }
            if (error) {
                val progress = pulse % 1f
                drawCircle(
                    color = glowTint.copy(alpha = (1f - progress) * 0.5f),
                    radius = r * (1.02f + progress * 0.22f),
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            clipPath(Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF4E9FF),
                            GtmColors.orbLavender.copy(alpha = 0.55f)
                        ),
                        center = Offset(c.x - r * 0.2f, c.y - r * 0.3f),
                        radius = r * 1.4f
                    ),
                    radius = r,
                    center = c
                )

                val drift = phase.toFloat()
                val diffusion = if (thinking) {
                    (sinF(phase * 0.8f) * 0.5f + 0.5f)
                } else {
                    0f
                }
                val orbit = r * (0.02f + if (thinking) 0.025f else 0.09f * activity)
                val orbitAngle = (
                    phase + spin.toFloat() * (PI.toFloat() / 180f) * 0.8f
                ).toDouble()
                val basePoints = listOf(
                    Offset(
                        c.x - r * 0.34f + sinF(drift * 0.5f) * r * 0.06f,
                        c.y - r * 0.34f + cosF(drift * 0.4f) * r * 0.05f
                    ),
                    Offset(
                        c.x + r * 0.38f + cosF(drift * 0.45f) * r * 0.04f,
                        c.y - r * 0.04f + sinF(drift * 0.5f) * r * 0.06f
                    ),
                    Offset(
                        c.x - r * 0.04f + sinF(drift * 0.35f) * r * 0.05f,
                        c.y + r * 0.42f + cosF(drift * 0.4f) * r * 0.03f
                    )
                )
                basePoints.forEachIndexed { index, basePoint ->
                    val point = basePoint + Offset(
                        cosF((orbitAngle + index * 2.1).toFloat()) * orbit,
                        sinF((orbitAngle + index * 2.1).toFloat()) * orbit * 0.8f
                    )
                    val color = activePalette[index]
                    val blobRadius = r * (
                        if (thinking) 0.95f + 0.3f * diffusion else 0.95f + 0.02f * activity
                    )
                    val blobAlpha = if (thinking) {
                        0.5f + 0.18f * diffusion
                    } else {
                        0.78f + 0.17f * activity
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = blobAlpha),
                                color.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = point,
                            radius = blobRadius
                        ),
                        radius = blobRadius,
                        center = point
                    )
                }

                // A small specular, not a veil, so the aurora stays visible.
                val highlight = Offset(c.x - r * 0.3f, c.y - r * 0.36f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.8f), Color.Transparent),
                        center = highlight,
                        radius = r * 0.36f
                    ),
                    radius = r * 0.36f,
                    center = highlight
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(c.x - r * 0.08f, c.y - r * 0.12f),
                        radius = r * 0.55f
                    ),
                    radius = r * 0.55f,
                    center = Offset(c.x - r * 0.08f, c.y - r * 0.12f)
                )

                // A hint of depth at the bottom of the sphere.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, activePalette[2].copy(alpha = 0.2f)),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            }

            drawCircle(
                color = rimTint.copy(alpha = 0.95f),
                radius = r,
                center = c,
                style = Stroke(1.8.dp.toPx())
            )
            drawCircle(
                color = rimTint.copy(alpha = 0.2f),
                radius = r * 1.012f,
                center = c,
                style = Stroke(4.dp.toPx())
            )
            drawCircle(
                color = glowTint.copy(alpha = 0.18f),
                radius = r * 0.97f,
                center = c,
                style = Stroke(8.dp.toPx())
            )
            drawCircle(
                color = rimTint.copy(alpha = 0.25f),
                radius = r * 1.1f,
                center = c,
                style = Stroke(1.dp.toPx())
            )
        }
    }
}
