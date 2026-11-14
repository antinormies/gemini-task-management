package id.ai.antaratask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.ai.antaratask.ui.theme.NumoTheme
import java.util.Locale

/**
 * Completion heatmap: columns are weeks, cells are days with a 0–3 intensity level.
 * Levels map to `line` / `successSoft` / `success` 55% / `success`.
 */
@Composable
fun Heatmap(
    weeks: List<List<Int>>,
    modifier: Modifier = Modifier,
    cell: androidx.compose.ui.unit.Dp = 34.dp
) {
    val c = NumoTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { level ->
                    val fill = when (level) {
                        0 -> c.line
                        1 -> c.successSoft
                        2 -> c.success.copy(alpha = 0.55f)
                        else -> c.success
                    }
                    Box(
                        Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(12.dp))
                            .background(fill)
                    )
                }
            }
        }
    }
}

/** Seven-day bar chart; values are relative weights. */
@Composable
fun UsageBars(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp
) {
    val c = NumoTheme.colors
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, value ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((height * (value / max).coerceIn(0.06f, 1f)))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == values.lastIndex) c.brand else c.brand.copy(alpha = 0.55f))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    labels.getOrElse(index) { "" },
                    style = NumoTheme.type.label,
                    color = c.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class StorageSegment(val label: String, val bytes: Long, val color: Color)

/** Single stacked storage bar plus legend. */
@Composable
fun StorageBar(segments: List<StorageSegment>, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    val total = segments.sumOf { it.bytes }.coerceAtLeast(1L)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(c.line),
            horizontalArrangement = Arrangement.Start
        ) {
            segments.forEach { segment ->
                val weight = (segment.bytes.toFloat() / total).coerceAtLeast(0.005f)
                Box(
                    Modifier
                        .weight(weight)
                        .height(12.dp)
                        .background(segment.color)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        segments.forEach { segment ->
            Row(
                Modifier.fillMaxWidth().height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(segment.color))
                Spacer(Modifier.width(8.dp))
                Text(segment.label, style = NumoTheme.type.meta, color = c.inkBody, modifier = Modifier.weight(1f))
                Text(formatBytes(segment.bytes), style = NumoTheme.type.meta, color = c.muted)
            }
        }
    }
}

/** Big inline figure with its caption, used by the System head strip. */
@Composable
fun StatFigure(value: String, label: String, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Column(modifier) {
        Text(value, style = NumoTheme.type.numberL, color = c.ink)
        Text(label, style = NumoTheme.type.meta, color = c.muted)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}
