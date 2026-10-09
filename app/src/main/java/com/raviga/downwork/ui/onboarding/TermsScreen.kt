package com.raviga.downwork.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.openLink
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.launch

@Composable
fun TermsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by container.session.config.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val legal = config.legal
    val me by container.session.me.collectAsStateWithLifecycle()
    // Agreed to an older version before: this is an update, and afterwards the client goes back to what they were doing.
    val updating = me?.consent?.terms != null

    ScreenScaffold(
        bottomBar = {
            BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Agree and continue", loading = busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { container.session.acceptLegal() }
                            .onSuccess {
                                if (nav.previousBackStackEntry != null) nav.popBackStack()
                                else nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                            }
                            .onFailure { error = it.userLine() }
                        busy = false
                    }
                })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(40.dp))
            Text(if (updating) "We've updated our terms" else "Before you start", style = DwType.title, color = Ink.ink)
            if (updating) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Our Terms and Privacy policy changed, including what DownWork can and can't build. Read them, then agree to carry on.",
                    style = DwType.body, color = Ink.graphite,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("What we don't keep", style = DwType.heading, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Recordings and files never leave your phone. Drafts are stored only on your phone. To write or price a brief, its text is sent to our AI, used once, and not stored by us. When you submit, we keep the brief to review and build it, and delete it when you accept the delivery.",
                style = DwType.body, color = Ink.ink,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "There is no account. Submitted projects and credits move to a new phone with a recovery key; drafts stay on this one. You can download or delete everything from Settings.",
                style = DwType.body, color = Ink.ink,
            )
            Spacer(Modifier.height(24.dp))
            Text("Privacy policy", style = DwType.body, color = Ink.cobalt, modifier = Modifier.clickable { openLink(context, legal.privacyUrl) }.padding(vertical = 6.dp))
            Text("Terms of service", style = DwType.body, color = Ink.cobalt, modifier = Modifier.clickable { openLink(context, legal.termsUrl) }.padding(vertical = 6.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                "Grievances: ${legal.grievance.officerName.ifBlank { "Grievance officer" }}, ${legal.grievance.email.ifBlank { "see Settings" }}. We reply within ${legal.grievance.responseDays} days.",
                style = DwType.caption, color = Ink.graphite,
            )
            Spacer(Modifier.height(8.dp))
            Text("${legal.companyName}. Terms ${legal.termsVersion}, privacy ${legal.privacyVersion}.", style = DwType.caption, color = Ink.graphite)
            Spacer(Modifier.height(32.dp))
        }
    }
}
