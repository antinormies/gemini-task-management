package id.ai.antaratask.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.ai.antaratask.data.Task
import id.ai.antaratask.ui.theme.NumoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// --------------------------------------------------------------------------- //
// Header
// --------------------------------------------------------------------------- //

@Composable
fun StreakPill(count: Int, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.onBrand)
            .semantics { contentDescription = "$count day streak" },
        contentAlignment = Alignment.Center
    ) {
        Text(count.toString(), style = NumoTheme.type.titleM, color = c.brand)
    }
}

@Composable
fun AvatarBadge(initials: String?, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Box(
        modifier
            .size(NumoTheme.size.avatar)
            .clip(CircleShape)
            .background(c.brandSoft)
            .border(2.dp, c.onBrand.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials?.take(2)?.uppercase(Locale.ROOT) ?: "—",
            style = NumoTheme.type.label,
            color = c.brandStrong
        )
    }
}

/**
 * Saturated header band. Compact mode (Jev tournament X1) keeps the band short so the
 * list stays the focus: `displayM` title, chips on the same band.
 */
@Composable
fun AppHeader(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    streak: Int? = null,
    avatarInitials: String? = null,
    onTitleClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chips: (@Composable FlowRowScope.() -> Unit)? = null
) {
    val c = NumoTheme.colors
    val t = NumoTheme.type
    val radius = NumoTheme.radius
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(accent)
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = NumoTheme.space.gutter)) {
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (streak != null && streak > 0) {
                    StreakPill(streak)
                    Spacer(Modifier.width(10.dp))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(radius.control)
                        .then(
                            if (onTitleClick != null) {
                                Modifier.clickable(onClick = onTitleClick)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = if (compact) t.displayM else t.displayL,
                        color = c.onBrand,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onTitleClick != null) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = "Change scope",
                            tint = c.onBrand,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                trailing?.invoke(this)
            }
            if (chips != null) {
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NumoTheme.space.sm),
                    verticalArrangement = Arrangement.spacedBy(NumoTheme.space.sm),
                    content = chips
                )
            }
            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        }
    }
}

// --------------------------------------------------------------------------- //
// Chips and toggles
// --------------------------------------------------------------------------- //

/** Filter chip. `onBand` renders the outlined-white variant used on the header band. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onBand: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    val background = when {
        selected && onBand -> c.onBrand
        selected -> c.brand
        else -> Color.Transparent
    }
    val content = when {
        selected && onBand -> c.brandStrong
        selected -> c.onBrand
        onBand -> c.onBrand
        else -> c.inkBody
    }
    Box(
        modifier
            .height(NumoTheme.size.chipHeight)
            .clip(NumoTheme.radius.pill)
            .background(background)
            .then(
                if (!selected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = if (onBand) c.onBrand.copy(alpha = 0.55f) else c.lineStrong,
                        shape = NumoTheme.radius.pill
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = NumoTheme.type.label, color = content, maxLines = 1)
    }
}

@Composable
fun ChipRow(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBand: Boolean = false
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(NumoTheme.space.sm)
    ) {
        items(items) { item ->
            FilterChip(
                label = item,
                selected = item == selected,
                onClick = { onSelect(item) },
                onBand = onBand
            )
        }
    }
}

/** Pill segmented control used for Board / List inside the header band. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onBand: Boolean = true,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    Row(
        modifier
            .height(NumoTheme.size.chipHeight)
            .clip(NumoTheme.radius.pill)
            .background(if (onBand) Color.Transparent else c.surfaceWarm)
            .then(
                if (onBand) {
                    Modifier.border(1.5.dp, c.onBrand.copy(alpha = 0.55f), NumoTheme.radius.pill)
                } else {
                    Modifier
                }
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .height(NumoTheme.size.chipHeight - 6.dp)
                    .clip(NumoTheme.radius.pill)
                    .background(if (active) (if (onBand) c.onBrand else c.brand) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = NumoTheme.type.label,
                    color = if (active) (if (onBand) c.brandStrong else c.onBrand) else (if (onBand) c.onBrand else c.muted)
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- //
// Task row
// --------------------------------------------------------------------------- //

@Composable
fun NumoCheckbox(
    checked: Boolean,
    onToggle: (() -> Unit)?,
    contentDescription: String?,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    val fill = if (checked) (tint ?: c.brand) else Color.Transparent
    Box(
        modifier
            .size(NumoTheme.size.checkbox)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .then(
                if (!checked) {
                    Modifier.border(2.dp, c.lineStrong, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .then(
                if (onToggle != null) {
                    Modifier
                        .clickable(role = Role.Checkbox, onClick = onToggle)
                        .semantics { this.role = Role.Checkbox }
                } else {
                    Modifier
                }
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (tint == null || tint == c.brand) c.onBrand else c.onSuccess,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PriorityDot(priority: String, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    val color = when (priority) {
        "urgent" -> c.danger
        "high" -> c.warning
        "medium" -> c.muted
        else -> c.lineStrong
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: Task,
    highlighted: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    showHandle: Boolean = false,
    projectDotColor: Color? = null
) {
    val c = NumoTheme.colors
    val haptics = LocalHapticFeedback.current
    val done = task.isDone
    val alpha by animateFloatAsState(if (done) 0.6f else 1f, label = "rowAlpha")
    val background by animateColorAsState(
        when {
            done -> c.surface
            highlighted -> c.surfaceWarm
            else -> c.surface
        },
        label = "rowBackground"
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = NumoTheme.size.rowHeight)
            .clip(NumoTheme.radius.row)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showHandle) {
            Column(
                Modifier
                    .padding(end = 10.dp)
                    .semantics { contentDescription = "Drag handle" },
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(2) {
                    Box(Modifier.width(16.dp).height(2.dp).clip(CircleShape).background(c.lineStrong))
                }
            }
        }
        NumoCheckbox(
            checked = done,
            onToggle = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
            contentDescription = if (done) "Reopen ${task.title}" else "Complete ${task.title}"
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = NumoTheme.type.titleM,
                color = c.ink.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
            )
            val meta = rowMeta(task, projectDotColor)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (projectDotColor != null) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(projectDotColor))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = meta,
                        style = NumoTheme.type.meta,
                        color = c.muted.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (task.priority == "urgent" || task.priority == "high") {
            Spacer(Modifier.width(8.dp))
            PriorityDot(task.priority)
        }
        val due = dueLabel(task)
        if (due != null) {
            Spacer(Modifier.width(10.dp))
            Text(due, style = NumoTheme.type.meta, color = c.muted.copy(alpha = alpha))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = NumoTheme.type.label, color = NumoTheme.colors.muted)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = NumoTheme.type.meta, color = NumoTheme.colors.mutedSoft)
        }
    }
}

@Composable
fun CompletionMeter(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    val fraction = if (total <= 0) 0f else done.toFloat() / total
    Column(modifier.fillMaxWidth()) {
        Text(
            "$total tasks · $done done",
            style = NumoTheme.type.meta,
            color = c.muted
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(c.line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.success)
            )
        }
    }
}

// --------------------------------------------------------------------------- //
// Actions
// --------------------------------------------------------------------------- //

@Composable
private fun ActionPill(
    label: String,
    onClick: () -> Unit,
    fill: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .fillMaxWidth()
            .height(NumoTheme.size.controlHeight)
            .clip(NumoTheme.radius.pill)
            .background(fill.copy(alpha = fill.alpha * alpha))
            .then(
                if (border != null) {
                    Modifier.border(1.5.dp, border.copy(alpha = border.alpha * alpha), NumoTheme.radius.pill)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(Modifier.width(10.dp))
        } else if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = NumoTheme.type.titleM, color = contentColor.copy(alpha = contentColor.alpha * alpha))
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    ActionPill(label, onClick, NumoTheme.colors.brand, NumoTheme.colors.onBrand, modifier, enabled = enabled, loading = loading, leading = leading)
}

@Composable
fun DestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ActionPill(label, onClick, NumoTheme.colors.dangerFill, NumoTheme.colors.onDanger, modifier, enabled = enabled)
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = NumoTheme.colors
    ActionPill(label, onClick, c.surface, c.ink, modifier, border = c.lineStrong, enabled = enabled)
}

/**
 * Action for a saturated brand canvas, where PrimaryButton would vanish into the fill.
 * Enabled: white pill, brand label. Disabled: outline + translucent label, which stays
 * legible on blue where a faded fill would not.
 */
@Composable
fun InvertedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = NumoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(NumoTheme.size.controlHeight)
            .clip(NumoTheme.radius.pill)
            .background(if (enabled) c.onBrand else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier
                } else {
                    Modifier.border(1.5.dp, c.onBrand.copy(alpha = 0.7f), NumoTheme.radius.pill)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = NumoTheme.type.titleM,
            color = if (enabled) c.brandStrong else c.onBrand.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(NumoTheme.size.minTouch)
            .clip(NumoTheme.radius.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = NumoTheme.type.titleM, color = NumoTheme.colors.inkBody)
    }
}

// --------------------------------------------------------------------------- //
// FABs and bottom bar
// --------------------------------------------------------------------------- //

@Composable
fun FabCluster(
    onMic: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    micDescription: String = "Start voice capture"
) {
    val c = NumoTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(NumoTheme.space.sm)
    ) {
        Box(
            Modifier
                .size(NumoTheme.size.micFab)
                .clip(CircleShape)
                .background(c.surface)
                .border(1.5.dp, c.line, CircleShape)
                .clickable(onClick = onMic)
                .semantics { contentDescription = micDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = c.brand, modifier = Modifier.size(22.dp))
        }
        Box(
            Modifier
                .size(NumoTheme.size.addFab)
                .clip(CircleShape)
                .background(c.brand)
                .clickable(onClick = onAdd)
                .semantics { contentDescription = "Add task" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(28.dp))
        }
    }
}

data class BottomItem(
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0
)

@Composable
fun NumoBottomBar(
    items: List<BottomItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.canvas)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = NumoTheme.size.bottomBar),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selectedIndex
                val color = if (active) c.brand else c.muted
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = NumoTheme.space.sm)
                        .semantics { contentDescription = item.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box {
                        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(NumoTheme.size.iconMd))
                        if (item.badge > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(c.dangerFill),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    item.badge.toString(),
                                    style = NumoTheme.type.label,
                                    color = c.onDanger
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        style = NumoTheme.type.label,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (active) c.brand else Color.Transparent)
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- //
// Surfaces
// --------------------------------------------------------------------------- //

@Composable
fun StackCard(
    modifier: Modifier = Modifier,
    color: Color = NumoTheme.colors.surface,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(NumoTheme.radius.card)
            .background(color)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumoSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = NumoTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.surface,
        contentColor = c.inkBody,
        scrimColor = c.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.lineStrong)
            )
        }
    ) { content() }
}

@Composable
fun NumoAlertDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Not now",
    destructive: Boolean = false
) {
    val c = NumoTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = NumoTheme.radius.card,
        containerColor = c.surface,
        titleContentColor = c.ink,
        textContentColor = c.inkBody,
        title = { Text(title, style = NumoTheme.type.displayM) },
        text = { Text(body, style = NumoTheme.type.bodyM, color = c.inkBody) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = NumoTheme.type.titleM,
                    color = if (destructive) c.dangerStrong else c.brandStrong
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = NumoTheme.type.titleM, color = c.muted)
            }
        }
    )
}

@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Row(
        modifier
            .clip(NumoTheme.radius.pill)
            .background(c.brand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = NumoTheme.type.titleM, color = c.onBrand)
    }
}

/** Error counterpart to ToastPill, used where the screen has no inline error block. */
@Composable
fun ErrorToastPill(text: String, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Row(
        modifier
            .clip(NumoTheme.radius.pill)
            .background(c.dangerSoft)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = NumoTheme.type.label, color = c.dangerStrong)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(NumoTheme.radius.card)
            .background(c.surfaceWarmSoft)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = NumoTheme.type.displayM, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = NumoTheme.type.bodyM, color = c.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth(0.7f))
        }
    }
}

@Composable
fun SkeletonRows(count: Int = 5, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NumoTheme.space.rowGap)) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(NumoTheme.size.rowHeight)
                    .clip(NumoTheme.radius.row)
                    .background(c.surfaceWarm.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun OfflineBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = NumoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(NumoTheme.radius.control)
            .background(c.warningSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Can't reach the server",
            style = NumoTheme.type.bodyM,
            color = c.inkBody,
            modifier = Modifier.weight(1f)
        )
        Row(
            Modifier
                .clip(NumoTheme.radius.pill)
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.inkBody, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry", style = NumoTheme.type.label, color = c.inkBody)
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = NumoTheme.colors
    val accent = tint ?: c.brand
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = NumoTheme.size.minTouch + 8.dp)
            .clip(NumoTheme.radius.control)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(NumoTheme.size.iconSm))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = NumoTheme.type.titleM,
            color = if (destructive) c.dangerStrong else c.ink,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(value, style = NumoTheme.type.meta, color = c.muted, maxLines = 1)
        }
    }
}

// --------------------------------------------------------------------------- //
// Formatting helpers shared by screens
// --------------------------------------------------------------------------- //

internal fun Task.isOverdue(now: LocalDateTime = LocalDateTime.now()): Boolean {
    val due = parseDue(dueAt) ?: return false
    return !isDone && due.isBefore(now)
}

internal fun parseDue(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    val zone = ZoneId.systemDefault()
    return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(raw) }
        .recoverCatching { Instant.parse(raw).atZone(zone).toLocalDateTime() }
        .recoverCatching { LocalDate.parse(raw.substring(0, 10)).atStartOfDay() }
        .getOrNull()
}

internal fun dueLabel(task: Task): String? {
    val due = parseDue(task.dueAt) ?: return null
    val today = LocalDate.now()
    val date = due.toLocalDate()
    val time = if (task.dueAllDay) "" else due.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
    return when {
        date == today -> time.ifEmpty { "Today" }
        date == today.plusDays(1) -> "Tmrw" + if (time.isEmpty()) "" else " $time"
        else -> {
            // Locale.ROOT has no month names on Android/ICU ("6 M11"), so use the device locale.
            val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }
    }
}

internal fun estimateLabel(minutes: Int?): String? = when {
    minutes == null -> null
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}.${(minutes % 60) * 10 / 60}h"
}

internal fun rowMeta(task: Task, projectDotColor: Color?): String {
    val parts = mutableListOf<String>()
    task.projectName?.let { parts.add(it) }
    estimateLabel(task.estimateMinutes)?.let { parts.add(it) }
    if (task.subtasks.isNotEmpty()) {
        val done = task.subtasks.count { it.isDone }
        parts.add("$done/${task.subtasks.size}")
    }
    if (task.tags.isNotEmpty()) parts.add("#" + task.tags.first().name)
    if (projectDotColor == null) return parts.joinToString("  ·  ")
    return parts.joinToString("  ·  ")
}

internal fun statusLabel(status: String): String = when (status) {
    "in_progress" -> "In progress"
    "waiting" -> "Waiting"
    "todo", "inbox", "backlog" -> "To do"
    "done" -> "Done"
    else -> status.replace("_", " ").replaceFirstChar { it.uppercase(Locale.ROOT) }
}

internal fun priorityLabel(priority: String): String = when (priority) {
    "urgent" -> "Urgent"
    "high" -> "High"
    "medium" -> "Medium"
    "low" -> "Low"
    else -> "None"
}
