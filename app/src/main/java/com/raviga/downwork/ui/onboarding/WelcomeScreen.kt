package com.raviga.downwork.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.Chip
import com.raviga.downwork.ui.components.ChipRow
import com.raviga.downwork.ui.components.HeroIllustration
import com.raviga.downwork.ui.components.Picture
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import kotlinx.coroutines.launch

/** First launch. The picture says what DownWork does; one line, the promises, one button. */
@Composable
fun WelcomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val config by container.session.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.paper)) {
        // Just under half the screen, so the four promises fit above the button without a scroll.
        val hero = (maxHeight * 0.46f).coerceIn(240.dp, 440.dp)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // The picture runs under the status bar (edge to edge); its height counts from the top of the screen.
                HeroIllustration(Picture.Welcome, height = hero, focusY = 0.1f)
                Column(Modifier.padding(horizontal = Dw.gutterWide).padding(top = 26.dp, bottom = 16.dp)) {
                    Text("Say what you want built.", style = DwType.hero, color = Ink.ink)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Describe it in your own words: say it, type it, or upload a document. We write the brief, quote it and build it.",
                        style = DwType.body,
                        color = Ink.graphite,
                    )
                    Spacer(Modifier.height(18.dp))
                    ChipRow(perRow = 2) {
                        Chip("Working app, delivered fast", icon = Icons.Outlined.Bolt)
                        Chip("Stays on your phone", icon = Icons.Outlined.Lock)
                        Chip("You approve the quote", dot = Ink.marigold)
                        Chip("Code handed to you", dot = Ink.teal)
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(horizontal = Dw.gutter)
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                PrimaryButton("Get started", onClick = {
                    scope.launch {
                        container.prefs.setWelcomeDone()
                        val me = container.session.me.value
                        val next = if (container.session.needsLegal(me, container.session.config.value)) Routes.TERMS else Routes.HOME
                        nav.navigate(next) { popUpTo(Routes.WELCOME) { inclusive = true } }
                    }
                })
                Spacer(Modifier.height(10.dp))
                Text(
                    "By ${config.legal.companyName}",
                    style = DwType.caption,
                    color = Ink.ash,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
