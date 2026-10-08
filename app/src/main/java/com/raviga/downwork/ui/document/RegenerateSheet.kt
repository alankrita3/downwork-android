package com.raviga.downwork.ui.document

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raviga.downwork.ui.components.DwTextField
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

/** Optional instruction, then "Regenerate". Stays open while the job runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegenerateSheet(
    heading: String,
    busyMessage: String?,
    busyProgress: Float?,
    onDismiss: () -> Unit,
    onRegenerate: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var instruction by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = { if (busyMessage == null) onDismiss() },
        sheetState = sheet,
        containerColor = Ink.paper,
        dragHandle = null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dw.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text("Regenerate $heading", style = DwType.heading, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text("Tell the AI what to change, or leave it blank for a fresh take.", style = DwType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(16.dp))
            if (busyMessage != null) {
                ProgressRule(progress = busyProgress?.takeIf { it > 0f })
                Spacer(Modifier.height(12.dp))
                Text(busyMessage, style = DwType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
            } else {
                DwTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = "Make it shorter, add offline mode…",
                    minLines = 2,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Regenerate", onClick = { onRegenerate(instruction) })
            }
        }
    }
}
