package dev.antinormies.gtm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import dev.antinormies.gtm.data.Project
import dev.antinormies.gtm.ui.theme.GtmColors
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuickAddDialog(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf("none") }
    var due by remember { mutableStateOf("") }
    var projectMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What needs doing?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Project", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { projectMenu = true }) {
                            Text(projects.firstOrNull { it.id == projectId }?.name ?: "No project")
                        }
                        DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("No project") }, onClick = { projectId = null; projectMenu = false })
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = { projectId = project.id; projectMenu = false }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Priority", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { priorityMenu = true }) {
                            Text(priority.replaceFirstChar { it.uppercase() })
                        }
                        DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                            listOf("none", "low", "medium", "high", "urgent").forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value.replaceFirstChar { it.uppercase() }) },
                                    onClick = { priority = value; priorityMenu = false }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text("Due (optional: yyyy-MM-dd HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dueIso = due.trim().takeIf { it.isNotBlank() }?.let {
                    runCatching {
                        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            .atZone(ZoneId.systemDefault()).toInstant().toString()
                    }.getOrNull()
                }
                onCreate(title, projectId, priority, dueIso)
            }) { Text("Add task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}