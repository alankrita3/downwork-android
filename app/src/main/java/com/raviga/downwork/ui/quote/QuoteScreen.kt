package com.raviga.downwork.ui.quote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BodyText
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.components.DwTextField
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.Hairline
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.KeyValueRow
import com.raviga.downwork.ui.components.NumberedSteps
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.components.TertiaryButton
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.util.Time

@Composable
fun QuoteScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: QuoteViewModel = viewModel(key = "quote_$projectId") { QuoteViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val quote = state.quote
    val config = state.config

    LaunchedEffect(state.submitted) {
        if (state.submitted) nav.navigate(Routes.status(projectId)) { popUpTo(Routes.HOME); launchSingleTop = true }
    }

    ScreenScaffold(
        topBar = { DwTopBar(onBack = { nav.popBackStack() }) },
        bottomBar = {
            if (quote == null || state.quoting) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when {
                    state.needsNewQuote -> PrimaryButton("Get a new quote", onClick = { vm.requestQuote() })
                    state.shortBy > 0 -> {
                        Text("You need ${state.shortBy} more ${if (state.shortBy == 1) "credit" else "credits"}.", style = DwType.secondary, color = Ink.graphite, modifier = Modifier.padding(bottom = 8.dp))
                        PrimaryButton("Buy credits", onClick = { nav.navigate(Routes.CREDITS) })
                    }
                    state.isResubmit -> PrimaryButton(
                        when {
                            state.due > 0 -> "Resubmit and pay ${state.due} more ${if (state.due == 1) "credit" else "credits"}"
                            state.due < 0 -> "Resubmit and get ${-state.due} credits back"
                            else -> "Resubmit"
                        },
                        loading = state.submitting, onClick = { vm.submit() },
                    )
                    else -> PrimaryButton("Submit and pay ${quote.credits} credits", loading = state.submitting, onClick = { vm.submit() })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            if (state.quoting || (quote == null && state.error == null)) {
                Spacer(Modifier.height(40.dp))
                Text(state.progressMessage ?: "Sizing the work", style = DwType.heading, color = Ink.ink)
                Spacer(Modifier.height(20.dp))
                ProgressRule(progress = state.progress?.takeIf { it > 0f })
                Spacer(Modifier.height(12.dp))
                Text("A quote takes about half a minute.", style = DwType.caption, color = Ink.graphite)
                return@Column
            }
            if (quote == null) {
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                TertiaryButton("Try again", onClick = { vm.requestQuote() })
                return@Column
            }
            val bracket = config.bracket(quote.bracketId)

            Text("${quote.credits} credits", style = DwType.display, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            if (bracket != null) {
                Text(bracket.name, style = DwType.body, color = Ink.ink)
                Text(bracket.blurb, style = DwType.secondary, color = Ink.graphite)
            }
            quote.inr?.let {
                Spacer(Modifier.height(4.dp))
                Text("About ₹${"%,d".format(it)} at ₹${"%,d".format(config.credits.creditValueInr)} a credit", style = DwType.caption, color = Ink.graphite)
            }
            if (state.needsNewQuote) {
                Spacer(Modifier.height(12.dp))
                Text("The brief changed since this quote.", style = DwType.secondary, color = Ink.amber)
            }

            Spacer(Modifier.height(Dw.sectionGap))
            SectionHeading("Timeline")
            Spacer(Modifier.height(12.dp))
            val tl = quote.timeline
            val weeks = when {
                tl != null && tl.minWeeks > 0 && tl.maxWeeks > tl.minWeeks -> "${tl.minWeeks} to ${tl.maxWeeks} weeks"
                tl != null && tl.maxWeeks > 0 -> "about ${tl.maxWeeks} weeks"
                else -> "about ${(quote.estimatedWorkingDays / 5).coerceAtLeast(2)} weeks"
            }
            Text("Ready $weeks after approval.", style = DwType.body, color = Ink.ink)
            Spacer(Modifier.height(4.dp))
            Text(config.quote.timelineNote, style = DwType.secondary, color = Ink.graphite)
            quote.expiresAt?.let {
                Spacer(Modifier.height(4.dp))
                Text("Quote valid until ${Time.shortDate(it)}.", style = DwType.caption, color = Ink.graphite)
            }

            if (quote.breakdown.isNotEmpty()) {
                Spacer(Modifier.height(Dw.sectionGap))
                SectionHeading("Where the credits go")
                Spacer(Modifier.height(8.dp))
                quote.breakdown.forEach { KeyValueRow(it.area, "${it.credits}") }
            }

            if (quote.assumptions.isNotEmpty()) {
                Spacer(Modifier.height(Dw.sectionGap))
                SectionHeading("Assumptions")
                Spacer(Modifier.height(12.dp))
                BodyText(quote.assumptions.joinToString("\n") { "- $it" })
            }

            quote.complexity?.drivers?.takeIf { it.isNotEmpty() }?.let { drivers ->
                Spacer(Modifier.height(16.dp))
                Text("What drives the size", style = DwType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(4.dp))
                BodyText(drivers.joinToString("\n") { "- " + it.trim().trimEnd('.') }, style = DwType.secondary, color = Ink.graphite)
            }

            Spacer(Modifier.height(Dw.sectionGap))
            SectionHeading("What happens next")
            Spacer(Modifier.height(8.dp))
            NumberedSteps(
                listOf(
                    "We review within a working day",
                    "You get comments or an approval",
                    "We build",
                    "You accept the repo or ask for a revision; ${config.revisions.includedRounds} revision rounds included",
                ),
            )

            Spacer(Modifier.height(Dw.sectionGap))
            SectionHeading("Handover")
            Spacer(Modifier.height(12.dp))
            Text("GitHub username", style = DwType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(6.dp))
            DwTextField(
                value = state.github,
                onValueChange = { vm.setGithub(it) },
                placeholder = "octocat",
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
            Spacer(Modifier.height(6.dp))
            Text("We transfer the finished repository to this account.", style = DwType.caption, color = Ink.graphite)
            Spacer(Modifier.height(16.dp))
            Hairline()
            val aws = state.me?.deliveryTargets?.aws
            DwRow(
                title = "AWS account",
                subtitle = when {
                    aws == null -> "Optional. Connect it and we deploy there too."
                    aws.verified -> "Connected, ${aws.accountId}"
                    else -> "${aws.accountId}, not verified yet"
                },
                subtitleColor = if (aws?.verified == true) Ink.moss else Ink.graphite,
                onClick = { nav.navigate(Routes.CONNECT_AWS) },
                horizontalPadding = 0.dp,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
