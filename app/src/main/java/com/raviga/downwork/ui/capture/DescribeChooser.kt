package com.raviga.downwork.ui.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

/** The three ways to describe a project; the value is the Capture route's `tab`. */
object DescribeWith {
    const val SPEAK = "speak"
    const val TYPE = "type"
    const val UPLOAD = "upload"
}

/**
 * "Describe your project" (or "Add to <title>"): Speak, Type or Upload a
 * document. Opened from Home and from the brief's "Add more".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescribeChooserSheet(projectTitle: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val retention by LocalAppContainer.current.session.config.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Ink.paper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text(
                if (projectTitle == null) "Describe your project" else "Add to ${projectTitle.ifBlank { "this project" }}",
                style = DwType.title,
                color = Ink.ink,
                modifier = Modifier.padding(horizontal = Dw.gutter),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (projectTitle == null) "Pick whichever is easiest. You can add more later." else "Whatever you add goes into the same brief.",
                style = DwType.caption,
                color = Ink.graphite,
                modifier = Modifier.padding(horizontal = Dw.gutter),
            )
            Spacer(Modifier.height(12.dp))
            DwRow(
                title = "Speak",
                subtitle = "Talk it through in English or Hindi. We write it up.",
                onClick = { onPick(DescribeWith.SPEAK) },
            )
            DwRow(
                title = "Type",
                subtitle = "Write or paste a description.",
                onClick = { onPick(DescribeWith.TYPE) },
            )
            DwRow(
                title = "Upload a document",
                subtitle = "A PDF, Word or text file you already have.",
                onClick = { onPick(DescribeWith.UPLOAD) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Recordings are deleted after ${retention.retention.audioDays} days and documents as soon as they're read. Only the text is kept, for your brief.",
                style = DwType.caption,
                color = Ink.graphite,
                modifier = Modifier.padding(horizontal = Dw.gutter),
            )
        }
    }
}
