package id.ai.antaratask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.ai.antaratask.data.Project
import id.ai.antaratask.data.Task
import id.ai.antaratask.ui.components.NumoCheckbox
import id.ai.antaratask.ui.components.NumoSheet
import id.ai.antaratask.ui.components.SectionTitle
import id.ai.antaratask.ui.components.StackCard
import id.ai.antaratask.ui.components.dueLabel
import id.ai.antaratask.ui.components.estimateLabel
import id.ai.antaratask.ui.components.statusLabel
import id.ai.antaratask.ui.theme.NumoTheme

/**
 * Task detail as stacked cards inside a 28dp sheet. Read-heavy: the sheet is where a
 * task is understood, so the chrome stays minimal and the destructive action sits last.
 */
@Composable
fun TaskDetailSheet(
    task: Task,
    projects: List<Project>,
    onClose: () -> Unit,
    onSave: (Task, Map<String, Any?>) -> Unit,
    onToggle: (Task) -> Unit,
    onArchive: (Task) -> Unit,
    onAddSubtask: (Task, String) -> Unit
) {
    val c = NumoTheme.colors
    val space = NumoTheme.space
    var subtask by remember { mutableStateOf("") }
    var confirmArchive by remember { mutableStateOf(false) }

    NumoSheet(onDismiss = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = space.gutter)
                .padding(bottom = 96.dp)
        ) {
            Text(
                "${task.shortKey} · ${statusLabel(task.status)} · v${task.version}",
                style = NumoTheme.type.label,
                color = c.muted
            )
            Spacer(Modifier.height(space.sm))
            Row(verticalAlignment = Alignment.Top) {
                Spacer(Modifier.height(4.dp))
                NumoCheckbox(
                    checked = task.isDone,
                    onToggle = { onToggle(task) },
                    contentDescription = if (task.isDone) "Reopen task" else "Complete task"
                )
                Spacer(Modifier.width(space.md))
                Text(task.title, style = NumoTheme.type.titleL, color = c.ink)
            }

            Spacer(Modifier.height(space.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScrollable(),
                horizontalArrangement = Arrangement.spacedBy(space.sm)
            ) {
                InfoChip(statusLabel(task.status), c.brandSoft, c.brandStrong)
                PriorityChip(task.priority) {
                    onSave(task, mapOf("priority" to nextPriority(task.priority)))
                }
                task.projectName?.let { InfoChip(it, c.surfaceWarm, c.inkBody) }
                dueLabel(task)?.let { InfoChip(it, c.surfaceWarm, c.inkBody) }
                estimateLabel(task.estimateMinutes)?.let { InfoChip(it, c.surfaceWarm, c.inkBody) }
            }

            Spacer(Modifier.height(space.lg))
            StackCard(color = c.surface) {
                Column {
                    KeyValueRow("Status", statusLabel(task.status))
                    KeyValueRow("Priority", task.priority.replaceFirstChar { it.uppercase() })
                    KeyValueRow("Project", task.projectName ?: "Inbox")
                    KeyValueRow("Estimate", estimateLabel(task.estimateMinutes) ?: "—")
                    KeyValueRow("Due", dueLabel(task) ?: "—")
                    KeyValueRow("Updated", task.events.lastOrNull()?.createdAt?.take(16)?.replace("T", " ") ?: "—")
                }
            }

            Spacer(Modifier.height(space.md))
            StackCard(color = c.surface) {
                Column {
                    SectionTitle("Subtasks", trailing = task.subtasks.size.toString())
                    Spacer(Modifier.height(space.sm))
                    task.subtasks.forEach { child ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NumoCheckbox(
                                checked = child.isDone,
                                onToggle = { onToggle(child) },
                                contentDescription = if (child.isDone) {
                                    "Reopen ${child.title}"
                                } else {
                                    "Complete ${child.title}"
                                }
                            )
                            Spacer(Modifier.width(space.md))
                            Text(
                                child.title,
                                style = NumoTheme.type.bodyM,
                                color = if (child.isDone) c.muted else c.inkBody
                            )
                        }
                    }
                    Spacer(Modifier.height(space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = subtask,
                            onValueChange = { subtask = it },
                            placeholder = { Text("Type subtask", style = NumoTheme.type.bodyM, color = c.muted) },
                            singleLine = true,
                            shape = NumoTheme.radius.control,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(space.sm))
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(c.brand)
                                .clickable {
                                    if (subtask.isNotBlank()) {
                                        onAddSubtask(task, subtask.trim())
                                        subtask = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = "Add subtask",
                                tint = c.onBrand,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(space.md))
                StackCard(color = c.surfaceWarmSoft) {
                    Column {
                        SectionTitle("Notes")
                        Spacer(Modifier.height(space.sm))
                        Text(task.description, style = NumoTheme.type.bodyL, color = c.inkBody)
                    }
                }
            }

            Spacer(Modifier.height(space.md))
            StackCard(color = c.surface) {
                Column {
                    SectionTitle("Move to")
                    Spacer(Modifier.height(space.sm))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScrollable(),
                        horizontalArrangement = Arrangement.spacedBy(space.sm)
                    ) {
                        listOf("todo", "in_progress", "waiting", "done").forEach { status ->
                            val active = task.status == status
                            InfoChip(
                                label = statusLabel(status),
                                background = if (active) c.brand else c.surfaceWarm,
                                content = if (active) c.onBrand else c.inkBody
                            ) {
                                if (!active) onSave(task, mapOf("status" to status))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(space.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(NumoTheme.radius.control)
                    .clickable { confirmArchive = true }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.dangerStrong, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(space.sm))
                Text("Archive task", style = NumoTheme.type.titleM, color = c.dangerStrong)
            }
        }
    }

    if (confirmArchive) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmArchive = false },
            shape = NumoTheme.radius.card,
            containerColor = c.surface,
            title = { Text("Archive this task?", style = NumoTheme.type.displayM, color = c.ink) },
            text = {
                Text(
                    "It leaves your board but stays in the project history.",
                    style = NumoTheme.type.bodyM,
                    color = c.inkBody
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirmArchive = false
                        onArchive(task)
                    }
                ) {
                    Text("Archive", style = NumoTheme.type.titleM, color = c.dangerStrong)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmArchive = false }) {
                    Text("Keep", style = NumoTheme.type.titleM, color = c.muted)
                }
            }
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    val c = NumoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NumoTheme.type.meta, color = c.muted, modifier = Modifier.width(96.dp))
        Text(value, style = NumoTheme.type.bodyM, color = c.inkBody)
    }
}

@Composable
private fun InfoChip(
    label: String,
    background: Color,
    content: Color,
    onClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .height(NumoTheme.size.chipHeight)
            .clip(NumoTheme.radius.pill)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = NumoTheme.type.label, color = content)
    }
}

@Composable
private fun PriorityChip(priority: String, onClick: () -> Unit) {
    val c = NumoTheme.colors
    val (fill, ink) = when (priority) {
        "urgent" -> c.dangerSoft to c.dangerStrong
        "high" -> c.warningSoft to c.inkBody
        else -> c.surfaceWarm to c.inkBody
    }
    InfoChip(
        label = priority.replaceFirstChar { it.uppercase() },
        background = fill,
        content = ink,
        onClick = onClick
    )
}

private fun nextPriority(current: String): String = when (current) {
    "none" -> "low"
    "low" -> "medium"
    "medium" -> "high"
    "high" -> "urgent"
    else -> "none"
}

@Composable
private fun Modifier.horizontalScrollable(): Modifier =
    this.horizontalScroll(rememberScrollState())
