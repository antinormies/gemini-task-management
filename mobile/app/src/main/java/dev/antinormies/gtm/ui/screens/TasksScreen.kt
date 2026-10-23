package dev.antinormies.gtm.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antinormies.gtm.MainViewModel
import dev.antinormies.gtm.UiState
import dev.antinormies.gtm.data.Task
import dev.antinormies.gtm.ui.components.EmptyState
import dev.antinormies.gtm.ui.components.TaskCard
import dev.antinormies.gtm.ui.components.TaskRow
import dev.antinormies.gtm.ui.components.isOverdue
import dev.antinormies.gtm.ui.theme.GtmColors
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

private data class BoardColumn(val key: String, val label: String)

@Composable
fun TasksScreen(
    state: UiState,
    viewModel: MainViewModel,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onToggle: (Task) -> Unit
) {
    var board by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        delay(250)
        viewModel.search(query)
    }

    val filtered = state.tasks.filter { task ->
        when (filter) {
            "today" -> {
                val due = task.dueAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                due == null || due.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() || isOverdue(task)
            }
            "overdue" -> isOverdue(task)
            else -> true
        }
    }
    val columns = listOf(
        BoardColumn("todo", "To do"),
        BoardColumn("prog", "In progress"),
        BoardColumn("waiting", "Waiting"),
        BoardColumn("done", "Done")
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(GtmColors.surfaceSoft)
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
            Text(
                "TODAY · " + LocalDate.now().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = GtmColors.muted
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My tasks", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                Surface(
                    color = GtmColors.ink,
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.clickable(onClick = onAdd)
                ) {
                    Text(
                        "+ New task",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                filtered.size.toString() + " tasks · " + filtered.count { it.isDone } + " done",
                style = MaterialTheme.typography.bodyMedium,
                color = GtmColors.muted
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search tasks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = GtmColors.line, shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(2.dp)) {
                        TabButton("Board", board) { board = true }
                        TabButton("List", !board) { board = false }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text("Add", color = GtmColors.accent) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip("All", filter == "all") { filter = "all" }
                FilterChip("Today", filter == "today") { filter = "today" }
                FilterChip("Overdue", filter == "overdue") { filter = "overdue" }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (filtered.isEmpty()) {
            EmptyState("Nothing here yet.", "Capture a task from the Speak tab or add one manually.")
        } else if (board) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(columns, key = { it.key }) { column ->
                    val items = filtered.filter { task ->
                        when (column.key) {
                            "todo" -> task.status == "inbox" || task.status == "todo"
                            "prog" -> task.status == "in_progress"
                            else -> task.status == column.key
                        }
                    }
                    Column(Modifier.width(268.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(column.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = GtmColors.muted)
                            Spacer(Modifier.width(6.dp))
                            Surface(color = GtmColors.line, shape = RoundedCornerShape(99.dp)) {
                                Text(
                                    items.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GtmColors.muted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        if (items.isEmpty()) {
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GtmColors.lineStrong)
                            ) {
                                Text(
                                    "No cards yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GtmColors.meta,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            items.forEach { task ->
                                TaskCard(task) { onOpen(task.id) }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 20.dp)) {
                listOf(
                    "To do" to filtered.filter { it.status == "inbox" || it.status == "todo" },
                    "In progress" to filtered.filter { it.status == "in_progress" },
                    "Waiting" to filtered.filter { it.status == "waiting" },
                    "Done" to filtered.filter { it.status == "done" }
                ).forEach { (label, group) ->
                    if (group.isNotEmpty()) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = GtmColors.muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        group.forEach { task ->
                            TaskRow(task, onOpen = { onOpen(task.id) }, onToggle = { onToggle(task) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) GtmColors.surface else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) GtmColors.ink else GtmColors.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) GtmColors.ink else Color.Transparent,
        shape = RoundedCornerShape(99.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, GtmColors.line),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else GtmColors.ink2,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
        )
    }
}
