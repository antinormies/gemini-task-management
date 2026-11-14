package id.ai.antaratask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.ai.antaratask.VoiceState
import id.ai.antaratask.data.Task
import id.ai.antaratask.ui.theme.NumoTheme

/**
 * Temporary bridge for screens that have not been migrated to the Numo system yet.
 * Delete this file once `grep -r "Pill\\|SectionLabel\\|TaskCard\\|VoiceOrb" ui/screens`
 * returns nothing (tracked in design.md §9).
 */

@Deprecated("Use StateOrb")
@Composable
fun VoiceOrb(state: VoiceState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StateOrb(state = state, onClick = onClick, modifier = modifier)
}

@Deprecated("Use a chip component")
@Composable
fun Pill(
    text: String,
    color: Color = Color.Unspecified,
    background: Color = Color.Unspecified
) {
    val c = NumoTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (background == Color.Unspecified) c.surfaceWarm else background)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = NumoTheme.type.label,
            color = if (color == Color.Unspecified) c.inkBody else color
        )
    }
}

@Deprecated("Use PriorityDot")
@Composable
fun PriorityPill(priority: String) {
    if (priority == "none" || priority.isEmpty()) return
    val c = NumoTheme.colors
    val (fill, ink) = when (priority) {
        "urgent" -> c.dangerSoft to c.dangerStrong
        "high" -> c.warningSoft to c.inkBody
        else -> c.surfaceWarm to c.inkBody
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fill)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(priorityLabel(priority), style = NumoTheme.type.label, color = ink)
    }
}

@Deprecated("Use SectionTitle")
@Composable
fun SectionLabel(text: String) {
    SectionTitle(text)
}

@Deprecated("Use TaskRow(task, highlighted, onToggle, onClick)")
@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    TaskRow(task = task, highlighted = false, onToggle = onToggle, onClick = onOpen, modifier = modifier)
}

@Deprecated("Use TaskRow")
@Composable
fun TaskCard(task: Task, modifier: Modifier = Modifier) {
    TaskRow(
        task = task,
        highlighted = false,
        onToggle = {},
        onClick = {},
        modifier = modifier
    )
}

@Deprecated("Use estimateLabel")
fun formatEstimate(minutes: Int?): String? = estimateLabel(minutes)

@Deprecated("Use the Task.isOverdue() extension")
fun isOverdue(task: Task): Boolean = task.isOverdue()
