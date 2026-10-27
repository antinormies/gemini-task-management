package id.ai.antaratask.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
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
import id.ai.antaratask.data.Proposal
import id.ai.antaratask.ui.components.Pill
import id.ai.antaratask.ui.theme.GtmColors

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
        containerColor = Color(0xFFFDFBFF)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("AI PROPOSAL", style = MaterialTheme.typography.labelMedium, color = GtmColors.speakMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                proposal.intent.replace("_", " "),
                style = MaterialTheme.typography.titleLarge,
                color = GtmColors.speakInk
            )
            Spacer(Modifier.height(6.dp))
            Text(proposal.message, style = MaterialTheme.typography.bodyMedium, color = GtmColors.speakMuted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (proposal.confidence > 0.0) {
                    Pill(
                        "Confidence " + (proposal.confidence * 100).toInt() + "%",
                        color = GtmColors.speakMuted,
                        background = GtmColors.speakAccentSoft
                    )
                }
                if (proposal.requiresConfirmation) {
                    Pill("Confirm", color = Color(0xFF9A5B12), background = Color(0xFFFFF0DD))
                }
            }
            Spacer(Modifier.height(14.dp))
            proposal.preview.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(item.op.uppercase(), style = MaterialTheme.typography.labelMedium, color = GtmColors.speakAccent)
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = GtmColors.speakInk)
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, color = GtmColors.speakMuted)
                }
                androidx.compose.material3.Divider(color = GtmColors.speakLine)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GtmColors.speakAccent,
                    contentColor = Color.White
                )
            ) { Text("Apply changes") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GtmColors.speakAccent),
                border = BorderStroke(1.dp, GtmColors.speakLine)
            ) { Text("Discard") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Destructive actions always require this confirmation. Nothing is applied automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = GtmColors.speakMuted
            )
        }
    }
}