package id.ai.antaratask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.ai.antaratask.MainViewModel
import id.ai.antaratask.UiState
import id.ai.antaratask.data.Health
import id.ai.antaratask.data.Usage
import id.ai.antaratask.ui.theme.GtmColors

@Composable
fun SystemScreen(state: UiState, viewModel: MainViewModel) {
    val usage = state.usage
    val health = state.health

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text("SYSTEM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        Text("Usage & device", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text("Everything GTM keeps on this phone and on your local server.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(usage?.tasks?.toString() ?: "—", "Tasks", "local SQLite", Modifier.weight(1f))
            StatCard(usage?.runs?.toString() ?: "—", "AI runs", "last 7 days", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(formatBytes(usage?.storage?.total ?: 0L), "Storage", "database + backups", Modifier.weight(1f))
            StatCard(usage?.tokens?.toString() ?: "—", "Tokens", "$" + String.format("%.2f", usage?.estimatedCost ?: 0.0), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        SettingsCard(title = "AI usage", meta = "Last 7 days") {
            if (usage == null) {
                Text("Loading usage…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                KeyValue("Runs", usage.runs.toString())
                KeyValue("Voice runs", usage.voiceRuns.toString())
                KeyValue("Tokens", usage.tokens.toString())
                KeyValue("Estimated cost", "$" + String.format("%.2f", usage.estimatedCost))
                KeyValue("Projects", usage.projects.toString())
                KeyValue("Paired devices", usage.devices.toString())
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard(title = "Storage", meta = "Local server") {
            KeyValue("SQLite database", formatBytes(usage?.storage?.dbBytes ?: 0L))
            KeyValue("Backups", formatBytes(usage?.storage?.backupsBytes ?: 0L))
            KeyValue("Logs", formatBytes(usage?.storage?.logsBytes ?: 0L))
            KeyValue("Workspace", formatBytes(usage?.storage?.workspaceBytes ?: 0L))
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard(title = "Engine", meta = health?.provider ?: "checking") {
            if (health == null) {
                Text("Loading health…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                KeyValue("Database", if (health.dbOk) "online" else "offline")
                KeyValue("AI provider", "self")
                KeyValue("Model", if (health.aiConfigured) health.aiModel else "not configured")
                KeyValue("Voice", if (health.voiceEnabled) health.voiceProvider else "disabled")
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard(title = "Paired devices", meta = state.devices.size.toString()) {
            if (state.devices.isEmpty()) {
                Text("No paired devices.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                state.devices.forEach { device ->
                    KeyValue(device.name, if (device.revoked) "revoked" else (device.lastSeenAt?.take(16)?.replace("T", " ") ?: "never seen"))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard(title = "Maintenance", meta = "local only") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.backup() }, modifier = Modifier.weight(1f)) { Text("Back up") }
                OutlinedButton(onClick = { viewModel.rebuildSearch() }, modifier = Modifier.weight(1f)) { Text("Rebuild search") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadSystem() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh diagnostics") }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "GTM 0.1.0 · Audio is handled by your phone's speech service. GTM receives only the transcript. Cloud audio is off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun StatCard(value: String, label: String, note: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsCard(title: String, meta: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}