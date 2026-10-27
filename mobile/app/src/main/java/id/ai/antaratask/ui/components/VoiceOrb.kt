package id.ai.antaratask.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import id.ai.antaratask.VoiceState
import id.ai.antaratask.ui.theme.GtmColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun sinF(value: Float): Float = sin(value.toDouble()).toFloat()
private fun cosF(value: Float): Float = cos(value.toDouble()).toFloat()

/**
 * The luminous pastel sphere from the voice reference: a slow-breathing glass
 * orb with drifting aurora blobs, a bright rim, and expanding rings while
 * listening. Everything is drawn on a Compose Canvas; no images.
 */
@Composable
fun VoiceOrb(state: VoiceState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val listening = state == VoiceState.LISTENING
    val thinking = state == VoiceState.THINKING
    val captured = state == VoiceState.CAPTURED
    val error = state == VoiceState.ERROR

    val transition = rememberInfiniteTransition(label = "voice-orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (thinking) 9000 else 17000, easing = LinearEasing)
        ),
        label = "phase"
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
            tween(if (listening) 1500 else 2800, easing = LinearEasing)
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension / 2f
            val r = base * 0.74f * breath

            // Soft halo around the glass sphere.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFFFFD7EC).copy(alpha = 0.35f),
                        GtmColors.orbLavender.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = r * 1.55f
                ),
                radius = r * 1.55f,
                center = c
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFC2E2).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(c.x - r * 0.4f, c.y - r * 0.45f),
                    radius = r * 0.85f
                ),
                radius = r * 0.85f,
                center = Offset(c.x - r * 0.4f, c.y - r * 0.45f)
            )

            if (listening) {
                repeat(2) { index ->
                    val progress = (pulse + index * 0.5f) % 1f
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - progress) * 0.5f),
                        radius = r * (1.02f + progress * 0.24f),
                        center = c,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
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

                val palette = when {
                    error -> listOf(GtmColors.orbRose, GtmColors.orbPink, GtmColors.orbLavender)
                    captured -> listOf(GtmColors.orbMint, GtmColors.orbBlue, Color.White)
                    else -> listOf(Color(0xFFFF8FC8), Color(0xFF7FB0FF), Color(0xFFA47BFF))
                }

                // Fixed color fields drift gently instead of rotating into a
                // uniform lavender wash.
                val drift = phase.toFloat()
                val points = listOf(
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
                points.forEachIndexed { index, point ->
                    val color = palette[index]
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = if (listening) 0.95f else 0.85f),
                                color.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = point,
                            radius = r * 0.95f
                        ),
                        radius = r * 0.95f,
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
                        colors = listOf(Color.Transparent, GtmColors.orbViolet.copy(alpha = 0.2f)),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            }

            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = r,
                center = c,
                style = Stroke(1.8.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = r * 1.012f,
                center = c,
                style = Stroke(4.dp.toPx())
            )
            drawCircle(
                color = GtmColors.orbLavender.copy(alpha = 0.18f),
                radius = r * 0.97f,
                center = c,
                style = Stroke(8.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = r * 1.1f,
                center = c,
                style = Stroke(1.dp.toPx())
            )
        }
    }
}
