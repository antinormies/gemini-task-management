package id.ai.antaratask.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.ai.antaratask.data.Project
import id.ai.antaratask.data.Task
import id.ai.antaratask.ui.components.Pill
import id.ai.antaratask.ui.components.PriorityPill
import id.ai.antaratask.ui.components.SectionLabel
import id.ai.antaratask.ui.components.formatEstimate
import id.ai.antaratask.ui.components.priorityLabel
import id.ai.antaratask.ui.components.statusLabel
import id.ai.antaratask.ui.theme.GtmColors
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.description) }
    var status by remember(task.id) { mutableStateOf(task.status) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var due by remember(task.id) {
        mutableStateOf(
            task.dueAt?.let {
                runCatching {
                    OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                }.getOrDefault("")
            } ?: ""
        )
    }
    var estimate by remember(task.id) { mutableStateOf(task.estimateMinutes?.toString() ?: "") }
    var tags by remember(task.id) { mutableStateOf(task.tags.joinToString(", ") { it.name }) }
    var confirmArchive by remember { mutableStateOf(false) }
    var newSubtask by remember(task.id) { mutableStateOf("") }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive this task?") },
            text = { Text("The task will be hidden, not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    onArchive(task)
                }) { Text("Archive", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.shortKey + " · " + statusLabel(task.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        title.ifBlank { "Task" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityPill(priority)
                task.projectName?.let { Pill(it) }
                formatEstimate(task.estimateMinutes)?.let { Pill(it) }
                if (task.status == "backlog") Pill("Backlog")
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Picker("Status", statusLabel(status), listOf("backlog", "research", "sprint", "review", "done").map { it to statusLabel(it) }) {
                status = it
            }
            Spacer(Modifier.height(8.dp))
            Picker("Priority", priorityLabel(priority), listOf("none", "low", "medium", "high", "urgent").map { it to priorityLabel(it) }) {
                priority = it
            }
            Spacer(Modifier.height(8.dp))
            Picker(
                "Project",
                projects.firstOrNull { it.id == projectId }?.name ?: "No project",
                listOf("" to "No project") + projects.map { it.id to it.name }
            ) { projectId = it.ifBlank { null } }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = due,
                onValueChange = { due = it },
                label = { Text("Due (yyyy-MM-dd HH:mm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = estimate,
                onValueChange = { estimate = it.filter { ch -> ch.isDigit() } },
                label = { Text("Estimate (minutes)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val dueIso = due.trim().takeIf { it.isNotBlank() }?.let {
                            runCatching {
                                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    .atZone(ZoneId.systemDefault()).toInstant().toString()
                            }.getOrNull()
                        }
                        onSave(
                            task,
                            mapOf(
                                "title" to title.trim(),
                                "description" to notes,
                                "status" to status,
                                "priority" to priority,
                                "project_id" to projectId,
                                "due_at" to dueIso,
                                "estimate_minutes" to estimate.toIntOrNull(),
                                "tags" to tags.split(",").map { value -> value.trim() }.filter { value -> value.isNotEmpty() }
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(onClick = { onToggle(task) }) {
                    Text(if (task.isDone) "Reopen" else "Complete")
                }
            }
            TextButton(onClick = { confirmArchive = true }) { Text("Archive", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Checklist")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    placeholder = { Text("Add a subtask") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    onAddSubtask(task, newSubtask)
                    newSubtask = ""
                }) { Text("Add") }
            }
            if (task.subtasks.isEmpty()) {
                Text("No subtasks yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                task.subtasks.forEach { child ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(if (child.isDone) "✓" else "○", color = if (child.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(child.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel("Activity")
            task.events.take(8).forEach { event ->
                Text(
                    event.eventType + " · " + event.actor + " · " + event.createdAt.take(16).replace("T", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Picker(label: String, current: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(current) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        open = false
                        onSelect(value)
                    })
                }
            }
        }
    }
}