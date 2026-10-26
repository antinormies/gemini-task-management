package dev.antinormies.gtm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.antinormies.gtm.MainViewModel
import dev.antinormies.gtm.UiState
import dev.antinormies.gtm.data.Task
import dev.antinormies.gtm.ui.components.EmptyState
import dev.antinormies.gtm.ui.components.TaskCard
import dev.antinormies.gtm.ui.components.TaskRow
import dev.antinormies.gtm.ui.components.isOverdue
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

private data class BoardColumn(val key: String, val label: String)

private val boardColumns = listOf(
    BoardColumn("backlog", "Backlog"),
    BoardColumn("research", "Research"),
    BoardColumn("sprint", "Sprint"),
    BoardColumn("review", "Review"),
    BoardColumn("done", "Done")
)

private fun phaseForTask(task: Task): String = when (task.status) {
    "research" -> "research"
    "sprint", "in_progress" -> "sprint"
    "review", "waiting" -> "review"
    "done" -> "done"
    else -> "backlog"
}

private fun statusForPhase(key: String): String = key

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

    var dragTask by remember { mutableStateOf<Task?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(Size.Zero) }
    var hoverPhase by remember { mutableStateOf<String?>(null) }
    var lastDragEnd by remember { mutableLongStateOf(0L) }
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    val boardState = rememberLazyListState()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    LaunchedEffect(query) {
        delay(250)
        viewModel.search(query)
    }

    LaunchedEffect(dragTask) {
        while (dragTask != null) {
            val edge = with(density) { 70.dp.toPx() }
            if (pointer.x < edge && boardState.canScrollBackward) {
                boardState.scrollBy(-18f)
            } else if (pointer.x > screenWidthPx - edge && boardState.canScrollForward) {
                boardState.scrollBy(18f)
            }
            delay(16)
        }
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "My tasks",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.clickable(onClick = onAdd)
                    ) {
                        Text(
                            "+ New task",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    filtered.size.toString() + " tasks · " + filtered.count { it.isDone } + " done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search tasks") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(9.dp)) {
                        Row(Modifier.padding(2.dp)) {
                            TabButton("Board", board) { board = true }
                            TabButton("List", !board) { board = false }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onAdd) { Text("Add", color = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip("All", filter == "all") { filter = "all" }
                    FilterChip("Today", filter == "today") { filter = "today" }
                    FilterChip("Overdue", filter == "overdue") { filter = "overdue" }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Long-press a card and drag it to another phase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            if (filtered.isEmpty()) {
                EmptyState("Nothing here yet.", "Capture a task from Speak or add one with New.")
            } else if (board) {
                LazyRow(
                    state = boardState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(boardColumns, key = { _, column -> column.key }) { index, column ->
                        val phaseTasks = filtered.filter { phaseForTask(it) == column.key }
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Column(
                                Modifier
                                    .width(248.dp)
                                    .onGloballyPositioned { coordinates ->
                                        columnBounds[column.key] = coordinates.boundsInRoot()
                                    }
                                    .background(
                                        if (hoverPhase == column.key) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        column.label.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(99.dp)
                                    ) {
                                        Text(
                                            phaseTasks.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(7.dp))
                                if (phaseTasks.isEmpty()) {
                                    Surface(
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(14.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            "Drop a task here",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                } else {
                                    phaseTasks.forEach { task ->
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coordinates ->
                                                    cardBounds[task.id] = coordinates.boundsInRoot()
                                                }
                                                .pointerInput(task.id) {
                                                    detectTapGestures {
                                                        if (System.currentTimeMillis() - lastDragEnd > 300L) {
                                                            onOpen(task.id)
                                                        }
                                                    }
                                                }
                                                .pointerInput(task.id) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { local ->
                                                            val bounds = cardBounds[task.id]
                                                            dragTask = task
                                                            pointer = (bounds?.topLeft ?: Offset.Zero) + local
                                                            cardSize = bounds?.size ?: Size.Zero
                                                            hoverPhase = null
                                                        },
                                                        onDrag = { change, amount ->
                                                            change.consume()
                                                            pointer += amount
                                                            hoverPhase = columnBounds.entries
                                                                .firstOrNull { it.value.contains(pointer) }
                                                                ?.key
                                                        },
                                                        onDragEnd = {
                                                            val taskNow = dragTask
                                                            val target = hoverPhase
                                                            if (taskNow != null && target != null && target != phaseForTask(taskNow)) {
                                                                viewModel.moveTask(taskNow, statusForPhase(target))
                                                            }
                                                            dragTask = null
                                                            hoverPhase = null
                                                            lastDragEnd = System.currentTimeMillis()
                                                        },
                                                        onDragCancel = {
                                                            dragTask = null
                                                            hoverPhase = null
                                                            lastDragEnd = System.currentTimeMillis()
                                                        }
                                                    )
                                                }
                                        ) {
                                            TaskCard(task)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                            if (index < boardColumns.lastIndex) {
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .padding(vertical = 6.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    listOf(
                        "Backlog" to filtered.filter { it.status == "backlog" || it.status == "inbox" || it.status == "todo" },
                        "Research" to filtered.filter { it.status == "research" },
                        "Sprint" to filtered.filter { it.status == "sprint" || it.status == "in_progress" },
                        "Review" to filtered.filter { it.status == "review" || it.status == "waiting" },
                        "Done" to filtered.filter { it.status == "done" }
                    ).forEach { (label, group) ->
                        if (group.isNotEmpty()) {
                            Text(
                                label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                            )
                            group.forEach { task ->
                                TaskRow(task, onOpen = { onOpen(task.id) }, onToggle = { onToggle(task) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }

        dragTask?.let { task ->
            val width = with(density) {
                if (cardSize.width > 0f) cardSize.width.toDp() else 220.dp
            }
            Box(Modifier.fillMaxSize().zIndex(10f)) {
                Card(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pointer.x - with(density) { width.toPx() } / 2f).roundToInt(),
                                (pointer.y - with(density) { 58.dp.toPx() } / 2f).roundToInt()
                            )
                        }
                        .width(width)
                        .zIndex(11f),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            task.shortKey,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    hoverPhase?.let { key -> boardColumns.firstOrNull { it.key == key }?.label } ?: "Drag to a phase",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(99.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(99.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
        )
    }
}
