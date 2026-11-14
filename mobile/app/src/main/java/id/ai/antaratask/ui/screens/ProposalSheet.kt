package id.ai.antaratask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.ai.antaratask.data.Proposal
import id.ai.antaratask.ui.components.GhostButton
import id.ai.antaratask.ui.components.NumoSheet
import id.ai.antaratask.ui.components.PrimaryButton
import id.ai.antaratask.ui.theme.NumoTheme
import kotlin.math.roundToInt

/**
 * Proposal review. The server has already validated the action; this sheet is the human
 * confirmation gate, so it leads with the transcript's meaning and the exact diff.
 */
@Composable
fun ProposalSheet(
    proposal: Proposal,
    onApply: () -> Unit,
    onDiscard: () -> Unit
) {
    val c = NumoTheme.colors
    val space = NumoTheme.space

    NumoSheet(onDismiss = onDiscard) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter)
                .padding(bottom = space.xxl)
        ) {
            Text("Review before saving", style = NumoTheme.type.label, color = c.muted)
            Spacer(Modifier.height(space.sm))
            Text(
                proposal.message.ifBlank { "Here is what I understood." },
                style = NumoTheme.type.bodyL,
                color = c.inkBody
            )
            Spacer(Modifier.height(space.md))
            proposal.preview.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(NumoTheme.radius.row)
                        .background(c.surfaceWarmSoft)
                        .padding(14.dp)
                ) {
                    Text(item.title, style = NumoTheme.type.titleM, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(item.summary, style = NumoTheme.type.meta, color = c.muted)
                }
                Spacer(Modifier.height(space.sm))
            }
            if (proposal.preview.isEmpty()) {
                Text(
                    "No field changes detected.",
                    style = NumoTheme.type.meta,
                    color = c.muted
                )
                Spacer(Modifier.height(space.sm))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Confidence ${(proposal.confidence * 100).roundToInt()}%",
                    style = NumoTheme.type.meta,
                    color = if (proposal.confidence < 0.6) c.dangerStrong else c.muted
                )
            }
            Spacer(Modifier.height(space.lg))
            PrimaryButton(
                label = "Apply",
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(space.xs))
            GhostButton("Discard", onDiscard, Modifier.fillMaxWidth())
        }
    }
}
