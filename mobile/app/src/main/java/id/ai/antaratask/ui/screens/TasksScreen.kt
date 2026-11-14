package id.ai.antaratask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.ai.antaratask.MainViewModel
import id.ai.antaratask.UiState
import id.ai.antaratask.data.Task
import id.ai.antaratask.ui.components.AppHeader
import id.ai.antaratask.ui.components.AvatarBadge
import id.ai.antaratask.ui.components.ChipRow
import id.ai.antaratask.ui.components.CompletionMeter
import id.ai.antaratask.ui.components.EmptyState
import id.ai.antaratask.ui.components.FabCluster
import id.ai.antaratask.ui.components.NumoSheet
import id.ai.antaratask.ui.components.OfflineBanner
import id.ai.antaratask.ui.components.SectionTitle
import id.ai.antaratask.ui.components.SegmentedToggle
import id.ai.antaratask.ui.components.SkeletonRows
import id.ai.antaratask.ui.components.TaskRow
import id.ai.antaratask.ui.components.dueLabel
import id.ai.antaratask.ui.components.isOverdue
import id.ai.antaratask.ui.components.statusLabel
import id.ai.antaratask.ui.theme.NumoTheme
import kotlinx.coroutines.delay

private data class StatusColumn(val status: String, val label: String)

private val StatusColumns = listOf(
    StatusColumn("todo", "To do"),
    StatusColumn("in_progress", "In progress"),
    StatusColumn("waiting", "Waiting"),
    StatusColumn("done", "Done")
)

/**
 * Tasks surface: compact brand band, cream/white list rows, and a status board one tap
 * away. Long-press moves a task between statuses (the deliberate replacement for free
 * drag), which keeps the gesture accessible and the card hit area unambiguous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: UiState,
    viewModel: MainViewModel,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onToggle: (Task) -> Unit,
    onVoice: () -> Unit
) {
    val c = NumoTheme.colors
    val space = NumoTheme.space
    var listMode by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("all") }
    var showSearch by remember { mutableStateOf(false) }
    var movingTask by remember { mutableStateOf<Task?>(null) }

    val filtered = remember(state.tasks, filter) {
        when (filter) {
            "today" -> state.tasks.filter { task ->
                val due = dueLabel(task)
                !task.isDone && (due != null || task.status == "in_progress")
            }
            "overdue" -> state.tasks.filter { it.isOverdue() }
            else -> state.tasks
        }
    }
    val doneCount = filtered.count { it.isDone }
    val projectColors = remember(state.projects) {
        state.projects.associate { it.id to parseColorOrNull(it.color) }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        AppHeader(
            title = when (filter) {
                "today" -> "Today"
                "overdue" -> "Overdue"
                else -> "All tasks"
            },
            accent = c.brand,
            compact = true,
            avatarInitials = initialsOf(state.account?.name),
            onTitleClick = { filter = when (filter) {
                "all" -> "today"
                "today" -> "overdue"
                else -> "all"
            } },
            trailing = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search tasks",
                    tint = c.onBrand,
                    modifier = Modifier
                        .clip(NumoTheme.radius.pill)
                        .clickable { showSearch = true }
                        .padding(10.dp)
                        .size(22.dp)
                )
            },
            chips = {
                ChipRow(
                    items = listOf("All", "Today", "Overdue"),
                    selected = when (filter) {
                        "today" -> "Today"
                        "overdue" -> "Overdue"
                        else -> "All"
                    },
                    onSelect = { label ->
                        filter = when (label) {
                            "Today" -> "today"
                            "Overdue" -> "overdue"
                            else -> "all"
                        }
                    },
                    onBand = true,
                    modifier = Modifier.weight(1f)
                )
                SegmentedToggle(
                    options = listOf("List", "Board"),
                    selectedIndex = if (listMode) 0 else 1,
                    onSelect = { listMode = it == 0 },
                    onBand = true
                )
            }
        )

        Box(Modifier.fillMaxSize()) {
            if (listMode) {
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = space.gutter,
                            end = space.gutter,
                            top = space.md,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(space.rowGap)
                    ) {
                        if (state.error != null) {
                            item("error") { OfflineBanner(onRetry = { viewModel.retryConnection() }) }
                        }
                        if (filter != "all" && filtered.isNotEmpty()) {
                            item("meter") { CompletionMeter(done = doneCount, total = filtered.size) }
                        }
                        when {
                            state.loading && state.tasks.isEmpty() -> item("skeleton") { SkeletonRows(5) }
                            filtered.isEmpty() -> item("empty") {
                                EmptyState(
                                    title = "Nothing here",
                                    body = when (filter) {
                                        "overdue" -> "No overdue tasks. Enjoy the quiet."
                                        "today" -> "Nothing due today. Add one or braindump it."
                                        else -> "Your board is clear. Add a task or speak one."
                                    },
                                    actionLabel = "Add a task",
                                    onAction = onAdd
                                )
                            }
                            else -> {
                                val groups = groupTasks(filtered, filter)
                                groups.forEach { (title, tasks) ->
                                    item(key = "h-$title") {
                                        SectionTitle(
                                            text = title,
                                            trailing = "${tasks.size}",
                                            modifier = Modifier.padding(top = space.sm)
                                        )
                                    }
                                    itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                                        TaskRow(
                                            task = task,
                                            highlighted = index % 2 == 0,
                                            onToggle = { onToggle(task) },
                                            onClick = { onOpen(task.id) },
                                            onLongPress = { movingTask = task },
                                            projectDotColor = task.projectId?.let { projectColors[it] }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                BoardBody(
                    tasks = filtered,
                    projectColors = projectColors,
                    onAdd = onAdd,
                    onOpen = onOpen,
                    onToggle = onToggle,
                    onLongPress = { movingTask = it }
                )
            }

            FabCluster(
                onMic = onVoice,
                onAdd = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = space.gutter, bottom = space.lg)
            )
        }
    }

    if (showSearch) {
        SearchOverlay(
            initialTasks = state.tasks,
            onDismiss = {
                showSearch = false
                viewModel.refresh()
            },
            onQuery = viewModel::search,
            onOpen = onOpen
        )
    }

    movingTask?.let { task ->
        NumoSheet(onDismiss = { movingTask = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter)
                    .padding(bottom = space.xxl)
            ) {
                Text(task.title, style = NumoTheme.type.titleL, color = c.ink, maxLines = 2)
                Spacer(Modifier.height(space.md))
                SectionTitle("Move to")
                Spacer(Modifier.height(space.sm))
                StatusColumns.forEach { column ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(NumoTheme.radius.control)
                            .clickable {
                                viewModel.moveTask(task, column.status)
                                movingTask = null
                            }
                            .padding(horizontal = space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(column.label, style = NumoTheme.type.titleM, color = c.ink)
                        Spacer(Modifier.weight(1f))
                        if (task.status == column.status) {
                            Text("current", style = NumoTheme.type.meta, color = c.muted)
                        }
                    }
                }
                Spacer(Modifier.height(space.sm))
                Text(
                    "Long-press any card to change its status.",
                    style = NumoTheme.type.meta,
                    color = c.muted
                )
            }
        }
    }
}

@Composable
private fun BoardBody(
    tasks: List<Task>,
    projectColors: Map<String, Color?>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onToggle: (Task) -> Unit,
    onLongPress: (Task) -> Unit
) {
    val c = NumoTheme.colors
    val space = NumoTheme.space
    Row(
        Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = space.gutter, vertical = space.md),
        horizontalArrangement = Arrangement.spacedBy(space.md)
    ) {
        StatusColumns.forEach { column ->
            val columnTasks = tasks.filter { task ->
                when (column.status) {
                    "todo" -> task.status in listOf("todo", "inbox", "backlog")
                    "done" -> task.isDone
                    else -> task.status == column.status
                }
            }
            val (tint, ink) = columnColors(column.status)
            Column(Modifier.width(232.dp).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(NumoTheme.radius.pill)
                        .background(tint)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(column.label, style = NumoTheme.type.label, color = ink)
                    Spacer(Modifier.width(6.dp))
                    Text("${columnTasks.size}", style = NumoTheme.type.label, color = ink.copy(alpha = 0.7f))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add ${column.label} task",
                        tint = ink,
                        modifier = Modifier
                            .clip(NumoTheme.radius.pill)
                            .clickable(onClick = onAdd)
                            .size(20.dp)
                    )
                }
                Spacer(Modifier.height(space.sm))
                Column(
                    Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(space.sm)
                ) {
                    if (columnTasks.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(NumoTheme.radius.row)
                                .background(c.surfaceWarmSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Empty", style = NumoTheme.type.meta, color = c.muted)
                        }
                    } else {
                        columnTasks.forEach { task ->
                            TaskRow(
                                task = task,
                                highlighted = false,
                                onToggle = { onToggle(task) },
                                onClick = { onOpen(task.id) },
                                onLongPress = { onLongPress(task) },
                                projectDotColor = task.projectId?.let { projectColors[it] }
                            )
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun columnColors(status: String): Pair<Color, Color> {
    val c = NumoTheme.colors
    return when (status) {
        "in_progress" -> c.warningSoft to c.inkBody
        "waiting" -> c.magentaSoft to c.magenta
        "done" -> c.successSoft to c.successStrong
        else -> c.brandSoft to c.brandStrong
    }
}

@Composable
private fun SearchOverlay(
    initialTasks: List<Task>,
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    val c = NumoTheme.colors
    val space = NumoTheme.space
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(350)
        onQuery(query)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .statusBarsPadding()
            .padding(horizontal = space.gutter)
    ) {
        Spacer(Modifier.height(space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search tasks", style = NumoTheme.type.bodyM, color = c.muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = c.muted) },
                singleLine = true,
                shape = NumoTheme.radius.control,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(space.sm))
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Close search",
                tint = c.inkBody,
                modifier = Modifier
                    .clip(NumoTheme.radius.pill)
                    .clickable(onClick = onDismiss)
                    .padding(10.dp)
                    .size(22.dp)
            )
        }
        Spacer(Modifier.height(space.md))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(space.rowGap)) {
            if (query.isBlank()) {
                item {
                    Text(
                        "Type to search titles, notes and tags.",
                        style = NumoTheme.type.meta,
                        color = c.muted
                    )
                }
            } else if (initialTasks.isEmpty()) {
                item {
                    EmptyState(
                        title = "No matches",
                        body = "Try a shorter word, or search a project name."
                    )
                }
            } else {
                itemsIndexed(initialTasks, key = { _, task -> task.id }) { index, task ->
                    TaskRow(
                        task = task,
                        highlighted = index % 2 == 0,
                        onToggle = {},
                        onClick = { onOpen(task.id) }
                    )
                }
            }
        }
    }
}

private fun groupTasks(tasks: List<Task>, filter: String): List<Pair<String, List<Task>>> {
    if (filter != "all") {
        return listOf(statusLabel(filter) to tasks)
    }
    val inbox = tasks.filter { it.projectName.isNullOrBlank() }
    val byProject = tasks.filterNot { it.projectName.isNullOrBlank() }
        .groupBy { it.projectName ?: "" }
        .toSortedMap()
    val groups = mutableListOf<Pair<String, List<Task>>>()
    if (inbox.isNotEmpty()) groups.add("Inbox" to inbox)
    byProject.forEach { (name, list) -> groups.add(name to list) }
    return groups
}

private fun initialsOf(name: String?): String? {
    if (name.isNullOrBlank()) return null
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when (parts.size) {
        0 -> null
        1 -> parts[0].take(1)
        else -> parts[0].take(1) + parts.last().take(1)
    }
}

private fun parseColorOrNull(hex: String?): Color? = runCatching {
    if (hex.isNullOrBlank()) null else Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
