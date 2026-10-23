package dev.antinormies.gtm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antinormies.gtm.data.Task
import dev.antinormies.gtm.ui.theme.GtmColors
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun priorityLabel(priority: String): String = when (priority) {
    "urgent" -> "Urgent"
    "high" -> "High"
    "medium" -> "Medium"
    "low" -> "Low"
    else -> "No priority"
}

fun priorityColor(priority: String): Color = when (priority) {
    "urgent" -> Color(0xFFE5484D)
    "high" -> Color(0xFFC47D00)
    "medium" -> Color(0xFF8A8F98)
    "low" -> Color(0xFF8A8F98)
    else -> Color(0xFF8A8F98)
}

fun statusLabel(status: String): String = when (status) {
    "inbox" -> "Inbox"
    "todo" -> "To do"
    "in_progress" -> "In progress"
    "waiting" -> "Waiting"
    "done" -> "Done"
    "cancelled" -> "Cancelled"
    else -> status
}

fun formatEstimate(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> rest.toString() + "m"
        rest == 0 -> hours.toString() + "h"
        else -> hours.toString() + "h " + rest + "m"
    }
}

fun dueDate(task: Task): OffsetDateTime? = task.dueAt?.let {
    runCatching { OffsetDateTime.parse(it) }.getOrNull()
}

fun dueLabel(task: Task): String? {
    val due = dueDate(task) ?: return null
    val local = due.atZoneSameInstant(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = local.toLocalDate()
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when {
        day.isBefore(today) -> "Overdue"
        day == today -> if (task.dueAllDay) "Today" else "Today " + time
        day == today.plusDays(1) -> "Tomorrow"
        else -> local.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

fun isOverdue(task: Task): Boolean {
    val due = dueDate(task) ?: return false
    return !task.isDone && due.toInstant().isBefore(java.time.Instant.now())
}

@Composable
fun Pill(text: String, color: Color = Color.Unspecified, background: Color = Color.Unspecified) {
    val fg = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    val bg = if (background == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHighest else background
    Surface(color = bg, shape = RoundedCornerShape(99.dp)) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PriorityPill(priority: String) {
    val color = priorityColor(priority)
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(99.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(Modifier.size(5.dp).background(color, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(priorityLabel(priority), color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    showProject: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                PriorityPill(task.priority)
                if (showProject && task.projectName != null) {
                    Text(task.projectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                dueLabel(task)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (isOverdue(task)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                formatEstimate(task.estimateMinutes)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TaskCard(task: Task, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.shortKey, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                formatEstimate(task.estimateMinutes)?.let { Pill(it) }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PriorityPill(task.priority)
                if (task.projectName != null) {
                    Text(task.projectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                dueLabel(task)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (isOverdue(task)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
