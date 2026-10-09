package com.raviga.downwork.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.Hairline
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.launch

/** Shown once, before anything is sent to an AI provider. Pops back when done. */
@Composable
fun AiConsentScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val config by container.session.config.collectAsStateWithLifecycle()
    var allow by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val providers = config.ai.providers.ifEmpty { listOf("our AI providers") }

    ScreenScaffold(
        topBar = { DwTopBar(onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Continue", enabled = allow, loading = busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { container.session.setAiProcessing(true) }
                            .onSuccess {
                                nav.previousBackStackEntry?.savedStateHandle?.set("ai_consent_granted", true)
                                nav.popBackStack()
                            }
                            .onFailure { error = it.userLine() }
                        busy = false
                    }
                })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(16.dp))
            Text("Before we send this to AI", style = DwType.title, color = Ink.ink)
            Spacer(Modifier.height(24.dp))
            Block("What is sent", "Your recordings, documents you upload and the words in them, plus anything you type about the project.")
            Block("To whom", providers.joinToString(", ") + ". They process it to write your brief and do not keep it to train models.")
            Block(
                "How long",
                "Recordings are deleted after ${config.retention.audioDays} days and documents as soon as they're read. " +
                    "Your notes are deleted ${config.retention.inputsDaysAfterClose} days after the project closes; the brief stays until you delete it.",
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Allow AI processing", style = DwType.body, color = Ink.ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = allow,
                    onCheckedChange = { allow = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.paper, checkedTrackColor = Ink.cobalt,
                        uncheckedThumbColor = Ink.paper, uncheckedTrackColor = Ink.ash, uncheckedBorderColor = Ink.ash,
                    ),
                )
            }
            Hairline()
            Spacer(Modifier.height(12.dp))
            Text("You can withdraw this any time in Settings. Without it, DownWork cannot write briefs.", style = DwType.caption, color = Ink.graphite)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(title, style = DwType.secondary, color = Ink.graphite)
        Spacer(Modifier.height(4.dp))
        Text(body, style = DwType.body, color = Ink.ink)
    }
}
