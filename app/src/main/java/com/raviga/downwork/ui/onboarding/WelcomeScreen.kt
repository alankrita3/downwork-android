package com.raviga.downwork.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(
        bottomBar = {
            BottomBar {
                PrimaryButton("Get started", onClick = {
                    scope.launch {
                        container.prefs.setWelcomeDone()
                        val me = container.session.me.value
                        val next = if (container.session.needsLegal(me, container.session.config.value)) Routes.TERMS else Routes.HOME
                        nav.navigate(next) { popUpTo(Routes.WELCOME) { inclusive = true } }
                    }
                })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.weight(1f))
            Text("Say what you want built.", style = DwType.display, color = Ink.ink)
            Spacer(Modifier.height(24.dp))
            Text("Describe it in your own words: say it, type it, or upload a document.", style = DwType.body, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text("We write it up as a proper brief.", style = DwType.body, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text("You approve, we build and hand over the code.", style = DwType.body, color = Ink.ink)
            Spacer(Modifier.weight(1f))
        }
    }
}
