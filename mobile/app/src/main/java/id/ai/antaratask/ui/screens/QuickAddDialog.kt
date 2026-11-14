package id.ai.antaratask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.ai.antaratask.data.Project
import id.ai.antaratask.ui.components.PrimaryButton
import id.ai.antaratask.ui.components.SectionTitle
import id.ai.antaratask.ui.components.StackCard
import id.ai.antaratask.ui.theme.NumoTheme
import java.time.LocalDate
import java.time.LocalDateTime

private data class WhenOption(val label: String, val due: () -> String?)

private val WhenOptions = listOf(
    WhenOption("Today · 18:00") { LocalDate.now().atTime(18, 0).toString() },
    WhenOption("Tomorrow · 09:00") { LocalDate.now().plusDays(1).atTime(9, 0).toString() },
    WhenOption("Next week") { LocalDate.now().plusWeeks(1).atTime(9, 0).toString() },
    WhenOption("No date") { null }
)

private val PriorityOptions = listOf("none", "low", "medium", "high", "urgent")

/**
 * Quick add composer: stacked white cards floating on the brand canvas. Numo's signature
 * creation surface. Only fields the API persists are offered — title, project, priority
 * and due — so nothing here silently disappears after saving.
 */
@Composable
fun QuickAddDialog(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String, String?) -> Unit
) {
    val c = NumoTheme.colors
    val space = NumoTheme.space
    var title by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf("none") }
    var dueIndex by remember { mutableStateOf(3) }
    val canSubmit = title.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(c.brand)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = space.gutter)
            ) {
                Spacer(Modifier.height(space.md))
                StackCard(color = c.surface) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(c.surfaceWarm)
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = c.inkBody,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(space.sm))
                            Pill(
                                label = projects.firstOrNull { it.id == projectId }?.name ?: "Inbox",
                                background = c.surfaceWarm,
                                content = c.brandStrong
                            )
                        }
                        Spacer(Modifier.height(space.md))
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            textStyle = NumoTheme.type.titleL.copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 76.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (title.isEmpty()) {
                                        Text(
                                            "Brain dump your tasks…",
                                            style = NumoTheme.type.titleL,
                                            color = c.mutedSoft
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(space.cardGap))
                StackCard(color = c.surface) {
                    Column {
                        SectionTitle("Project")
                        Spacer(Modifier.height(space.sm))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(space.sm)
                        ) {
                            SelectChip("Inbox", projectId == null) { projectId = null }
                            projects.forEach { project ->
                                SelectChip(project.name, projectId == project.id) {
                                    projectId = project.id
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(space.cardGap))
                StackCard(color = c.surface) {
                    Column {
                        SectionTitle("When")
                        Spacer(Modifier.height(space.sm))
                        WhenOptions.forEachIndexed { index, option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clip(NumoTheme.radius.control)
                                    .clickable { dueIndex = index }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    option.label,
                                    style = NumoTheme.type.bodyM,
                                    color = if (dueIndex == index) c.ink else c.inkBody,
                                    modifier = Modifier.weight(1f)
                                )
                                if (dueIndex == index) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = c.brand,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(space.cardGap))
                StackCard(color = c.surface) {
                    Column {
                        SectionTitle("Priority")
                        Spacer(Modifier.height(space.sm))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(space.sm)
                        ) {
                            PriorityOptions.forEach { option ->
                                SelectChip(
                                    label = if (option == "none") "None" else option.replaceFirstChar { it.uppercase() },
                                    selected = priority == option
                                ) { priority = option }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(space.lg))
                PrimaryButton(
                    label = "Add task",
                    onClick = {
                        onCreate(
                            title.trim(),
                            projectId,
                            priority,
                            WhenOptions[dueIndex].due()
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(space.huge))
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(end = space.gutter, bottom = space.lg)
                    .size(NumoTheme.size.submitFab)
                    .clip(CircleShape)
                    .background(if (canSubmit) c.surface else c.surface.copy(alpha = 0.55f))
                    .clickable(enabled = canSubmit) {
                        onCreate(title.trim(), projectId, priority, WhenOptions[dueIndex].due())
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "Save task",
                    tint = if (canSubmit) c.brand else c.muted,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = NumoTheme.colors
    Box(
        Modifier
            .height(NumoTheme.size.chipHeight)
            .clip(NumoTheme.radius.pill)
            .background(if (selected) c.brand else c.surfaceWarm)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = NumoTheme.type.label,
            color = if (selected) c.onBrand else c.inkBody
        )
    }
}

@Composable
private fun Pill(label: String, background: Color, content: Color) {
    Box(
        Modifier
            .height(NumoTheme.size.chipHeight)
            .clip(NumoTheme.radius.pill)
            .background(background)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = NumoTheme.type.label, color = content)
    }
}
