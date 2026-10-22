package dev.antinormies.gtm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antinormies.gtm.data.Proposal
import dev.antinormies.gtm.ui.components.Pill
import dev.antinormies.gtm.ui.theme.GtmColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalSheet(
    proposal: Proposal,
    onApply: () -> Unit,
    onDiscard: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDiscard,
        sheetState = sheetState,
        containerColor = GtmColors.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("GEMINI PROPOSAL", style = MaterialTheme.typography.labelMedium, color = GtmColors.muted)
            Spacer(Modifier.height(4.dp))
            Text(proposal.intent.replace("_", " "), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(proposal.message, style = MaterialTheme.typography.bodyMedium, color = GtmColors.ink2)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Confidence " + (proposal.confidence * 100).toInt() + "%")
                if (proposal.requiresConfirmation) Pill("Confirm", GtmColors.warning)
            }
            Spacer(Modifier.height(14.dp))
            proposal.preview.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(item.op.uppercase(), style = MaterialTheme.typography.labelMedium, color = GtmColors.accent)
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, color = GtmColors.muted)
                }
                androidx.compose.material3.Divider(color = GtmColors.line)
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply changes") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Destructive actions always require this confirmation. Nothing is applied automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = GtmColors.meta
            )
        }
    }
}
