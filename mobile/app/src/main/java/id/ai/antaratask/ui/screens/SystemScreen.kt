package id.ai.antaratask.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.unit.dp
import id.ai.antaratask.MainViewModel
import id.ai.antaratask.UiState
import id.ai.antaratask.data.Usage
import id.ai.antaratask.ui.components.DestructiveButton
import id.ai.antaratask.ui.components.NumoAlertDialog
import id.ai.antaratask.ui.components.SecondaryButton
import id.ai.antaratask.ui.components.SectionTitle
import id.ai.antaratask.ui.components.SettingsRow
import id.ai.antaratask.ui.components.SkeletonRows
import id.ai.antaratask.ui.components.StackCard
import id.ai.antaratask.ui.components.StatFigure
import id.ai.antaratask.ui.components.StorageBar
import id.ai.antaratask.ui.components.StorageSegment
import id.ai.antaratask.ui.theme.NumoTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * System / diagnostics. Jev chose list rows over a stat-tile grid here: the screen is
 * scanned for a specific fact, so values belong right-aligned next to their labels.
 */
@Composable
fun SystemScreen(state: UiState, viewModel: MainViewModel) {
    val c = NumoTheme.colors
    val t = NumoTheme.type
    val space = NumoTheme.space
    var confirmUnpair by remember { mutableStateOf(false) }
    val usage: Usage? = state.usage

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = space.gutter)
    ) {
        Spacer(Modifier.height(space.md))
        Text("System", style = t.displayL, color = c.ink)
        Spacer(Modifier.height(space.xs))
        Text("Your workspace at a glance", style = t.bodyM, color = c.muted)
        Spacer(Modifier.height(space.lg))

        if (state.error != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(NumoTheme.radius.control)
                    .background(c.dangerSoft)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.error ?: "",
                    style = t.bodyM,
                    color = c.dangerStrong,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Retry",
                    style = t.label,
                    color = c.dangerStrong,
                    modifier = Modifier
                        .clip(NumoTheme.radius.pill)
                        .background(c.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { viewModel.retryConnection() }
                )
            }
            Spacer(Modifier.height(space.md))
        }

        if (state.loading && usage == null && state.health == null) {
            SkeletonRows(3)
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space.lg)
            ) {
                StatFigure(
                    value = (usage?.tasks ?: state.tasks.size).toString(),
                    label = "tasks",
                    modifier = Modifier.weight(1f)
                )
                StatFigure(
                    value = state.tasks.count { it.isDone }.toString(),
                    label = "done",
                    modifier = Modifier.weight(1f)
                )
                StatFigure(
                    value = (usage?.runs ?: 0).toString(),
                    label = "AI requests",
                    modifier = Modifier.weight(1f)
                )
                StatFigure(
                    value = (usage?.voiceRuns ?: 0).toString(),
                    label = "voice notes",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(space.lg))
            StackCard(color = c.surfaceWarm) {
                Column {
                    SectionTitle("AI usage · 30 days")
                    Spacer(Modifier.height(space.sm))
                    Text(
                        formatTokens(usage?.tokens ?: 0L),
                        style = t.numberL,
                        color = c.ink
                    )
                    Text("tokens processed", style = t.meta, color = c.muted)
                    Spacer(Modifier.height(space.sm))
                    SettingsRow(
                        icon = Icons.Rounded.Insights,
                        label = "Estimated cost",
                        value = formatCost(usage?.estimatedCost ?: 0.0),
                        tint = c.brand
                    )
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Voice captures",
                        value = (usage?.voiceRuns ?: 0).toString(),
                        tint = c.success
                    )
                }
            }

            if (usage != null && usage.storage.total > 0) {
                Spacer(Modifier.height(space.md))
                StackCard(color = c.surface) {
                    Column {
                        SectionTitle("Storage", trailing = formatBytes(usage.storage.total))
                        Spacer(Modifier.height(space.sm))
                        StorageBar(
                            segments = listOf(
                                StorageSegment("Database", usage.storage.dbBytes, c.brand),
                                StorageSegment("Backups", usage.storage.backupsBytes, c.success),
                                StorageSegment("Logs", usage.storage.logsBytes, c.warning),
                                StorageSegment("Workspace", usage.storage.workspaceBytes, c.magenta)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(space.md))
            StackCard(color = c.surface) {
                Column {
                    SectionTitle("Engine")
                    Spacer(Modifier.height(space.xs))
                    SettingsRow(
                        icon = Icons.Rounded.Settings,
                        label = "AI provider",
                        value = state.health?.provider ?: "—",
                        tint = c.brand
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Insights,
                        label = "Model",
                        value = state.health?.aiModel?.ifBlank { "—" } ?: "—",
                        tint = c.brand
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Storage,
                        label = "Database",
                        value = if (state.health?.dbOk == true) "Healthy" else "Unknown",
                        tint = if (state.health?.dbOk == true) c.success else c.warning
                    )
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Speech",
                        value = if (state.health?.voiceEnabled == true) {
                            "On-device when available"
                        } else {
                            "Unavailable"
                        },
                        tint = c.success
                    )
                    SettingsRow(
                        icon = Icons.Rounded.CloudUpload,
                        label = "Interpretation",
                        value = if (state.health?.aiConfigured == true) "Configured" else "Not configured",
                        tint = if (state.health?.aiConfigured == true) c.success else c.warning
                    )
                }
            }

            Spacer(Modifier.height(space.md))
            StackCard(color = c.surface) {
                Column {
                    SectionTitle("Devices")
                    Spacer(Modifier.height(space.xs))
                    if (state.devices.isEmpty()) {
                        Text("No paired device reported.", style = t.meta, color = c.muted)
                    } else {
                        state.devices.forEach { device ->
                            SettingsRow(
                                icon = Icons.Rounded.Smartphone,
                                label = device.name.ifBlank { "Android device" },
                                value = device.lastSeenAt?.take(10) ?: "—",
                                tint = if (device.revoked) c.danger else c.brand
                            )
                        }
                    }
                    Spacer(Modifier.height(space.sm))
                    DestructiveButton(
                        label = "Unpair this phone",
                        onClick = { confirmUnpair = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(space.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                SecondaryButton(
                    label = "Back up now",
                    onClick = { viewModel.backup() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    label = "Rebuild index",
                    onClick = { viewModel.rebuildSearch() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.height(14.dp)
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    "AntaraTask 0.1.0 · Audio stays on your phone. Only the transcript is sent.",
                    style = t.meta,
                    color = c.muted
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (confirmUnpair) {
        NumoAlertDialog(
            title = "Unpair this phone?",
            body = "The device token is removed and you will need to sign in with Google again.",
            confirmLabel = "Unpair",
            dismissLabel = "Keep",
            destructive = true,
            onConfirm = {
                confirmUnpair = false
                viewModel.logout()
            },
            onDismiss = { confirmUnpair = false }
        )
    }
}

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.ROOT, "%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

private fun formatCost(cost: Double): String =
    if (cost < 0.01) "<$0.01" else String.format(Locale.ROOT, "$%.2f", cost)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb)
    val mb = kb / 1024.0
    return if (mb < 1024) String.format(Locale.ROOT, "%.1f MB", mb) else String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}
