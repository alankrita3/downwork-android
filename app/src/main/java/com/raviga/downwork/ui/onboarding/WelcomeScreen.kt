package com.raviga.downwork.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.Chip
import com.raviga.downwork.ui.components.ChipRow
import com.raviga.downwork.ui.components.HeroIllustration
import com.raviga.downwork.ui.components.Illustration
import com.raviga.downwork.ui.components.Picture
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import kotlinx.coroutines.launch

/** One step of the flow on Welcome: its picture, a title and one line. */
private data class Step(val title: String, val body: String, val picture: Picture)

private val steps = listOf(
    Step(
        "Describe it your way",
        "Say it, type it, or upload a document you already have. We turn it into a clear brief you can edit.",
        Picture.Describe,
    ),
    Step("See the price first", "You get a quote and a timeline before anything starts. Nothing is charged until you submit.", Picture.Quote),
    // The founder's exact wording (iOS 489412c); don't edit it without her approval.
    Step(
        "Built by AI, checked by experts",
        "AI builds your project at speed. Experts recommend the right tech stack, or use yours, and review the work before it reaches you.",
        Picture.AIBuild,
    ),
    Step(
        "Ready before you know it",
        "A working project, ready for your review, with the code, a handover guide and simple steps to make it live.",
        Picture.Delivered,
    ),
)

/**
 * First launch. Page one says what DownWork does; four more show the flow with its pictures (describe,
 * see the price, built by AI and checked by experts, ready), as on iOS. "Get started" is on every page,
 * so nobody has to swipe. Who-builds copy exists only here, in the founder's approved words.
 */
@Composable
fun WelcomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val config by container.session.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { steps.size + 1 })
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.paper)) {
        // Heights count from the top of the screen: the pictures run under the status bar.
        val hero = (maxHeight * 0.46f).coerceIn(240.dp, 440.dp)
        val picture = (maxHeight * 0.34f).coerceIn(200.dp, 320.dp)
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page == 0) Intro(hero) else StepPage(steps[page - 1], number = page, pictureHeight = picture)
            }
            PageDots(
                count = steps.size + 1,
                current = pager.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 12.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(horizontal = Dw.gutter)
                    .padding(bottom = 8.dp),
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

@Composable
private fun Intro(heroHeight: Dp) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroIllustration(Picture.Welcome, height = heroHeight, focusY = 0.1f)
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
                Chip("Working project delivered", icon = Icons.Outlined.Check)
                Chip("Super fast delivery", icon = Icons.Outlined.Bolt)
                Chip("You approve the quote", dot = Ink.marigold)
                Chip("Code handed to you", dot = Ink.teal)
            }
        }
    }
}

@Composable
private fun StepPage(step: Step, number: Int, pictureHeight: Dp) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dw.gutterWide)
            .padding(top = statusBar + 24.dp, bottom = 16.dp),
    ) {
        Illustration(step.picture, height = pictureHeight, radius = Dw.tileRadius)
        Spacer(Modifier.height(28.dp))
        Text("Step $number of ${steps.size}", style = DwType.secondaryMedium, color = Ink.teal)
        Spacer(Modifier.height(8.dp))
        Text(step.title, style = DwType.hero, color = Ink.ink)
        Spacer(Modifier.height(12.dp))
        Text(step.body, style = DwType.body, color = Ink.graphite)
    }
}

/** Where you are in a short sequence: the current page is a teal pill, the rest small dots. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val on = index == current
            val width by animateDpAsState(if (on) 18.dp else 6.dp, tween(200), label = "dot")
            Box(Modifier.width(width).height(6.dp).clip(CircleShape).background(if (on) Ink.teal else Ink.ruleStrong))
        }
    }
}
