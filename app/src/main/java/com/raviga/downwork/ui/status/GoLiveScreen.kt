package com.raviga.downwork.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.Delivery
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.data.api.SectionIds
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.Illustration
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.Picture
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.tile
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.openLink
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

/** "Make it live": a short checklist from the delivered repository to customers using it. */
@Composable
fun GoLiveScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val context = LocalContext.current
    val project = state.project
    val delivery = project?.delivery

    ScreenScaffold(topBar = { DwTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Dw.gutter)) }
            delivery == null -> ErrorState("These steps appear once the team hands over your code.", Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
                // The brief on this phone, else the copy the server still holds.
                val brief = container.drafts.get(project.id)?.document ?: project.document?.asDocument()
                val steps = GoLiveLogic.steps(
                    delivery = delivery,
                    status = project.status,
                    githubUsername = project.submission?.githubUsername?.ifBlank { null }
                        ?: container.session.me.value?.deliveryTargets?.githubUsername?.ifBlank { null },
                    platforms = GoLiveLogic.platforms(brief?.section(SectionIds.PLATFORMS)?.body.orEmpty()),
                    hasPaidServices = brief?.section(SectionIds.INTEGRATIONS)?.body.orEmpty().isNotBlank(),
                    includedRounds = state.config.revisions.includedRounds,
                )
                Spacer(Modifier.height(4.dp))
                Illustration(Picture.Delivered, height = 180.dp, radius = Dw.tileRadius)
                Spacer(Modifier.height(20.dp))
                Text("Make it live", style = DwType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your project already works on our servers. These steps move it to accounts you own and put it in front of customers.",
                    style = DwType.secondary,
                    color = Ink.graphite,
                )
                Spacer(Modifier.height(22.dp))
                steps.forEachIndexed { index, step ->
                    StepTile(index + 1, step, delivery, onGuide = { url -> openLink(context, url) }, onConnectAws = { nav.navigate(Routes.CONNECT_AWS) })
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(10.dp))
                val commentsOpen = project.contentDeletedAt == null && !ProjectStatus.isTerminal(project.status)
                Text(
                    if (commentsOpen) "Stuck on a step? Write to the team in this project's comments."
                    else "Stuck on a step? Write to ${state.config.legal.supportEmail}.",
                    style = DwType.caption,
                    color = Ink.ash,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StepTile(number: Int, step: GoLiveLogic.Step, delivery: Delivery, onGuide: (String) -> Unit, onConnectAws: () -> Unit) {
    Row(
        Modifier
            .tile(padding = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (step.done) "Done: ${step.title}" else "Step $number: ${step.title}" },
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (step.done) Ink.teal else Ink.ink), contentAlignment = Alignment.Center) {
            if (step.done) Icon(Icons.Outlined.Check, contentDescription = null, tint = Ink.surface, modifier = Modifier.size(15.dp))
            else Text("$number", style = DwType.chip, color = Ink.surface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = DwType.bodyMedium, color = if (step.done) Ink.graphite else Ink.ink)
            Spacer(Modifier.height(6.dp))
            Text(step.detail, style = DwType.secondary, color = Ink.graphite)
            when (step.action) {
                GoLiveLogic.Action.OpenGuide -> delivery.handover?.let { url ->
                    InlineAction("Open the guide", onClick = { onGuide(url) }, modifier = Modifier.offset(x = (-8).dp))
                }
                GoLiveLogic.Action.ConnectAws -> InlineAction("Connect AWS", onClick = onConnectAws, modifier = Modifier.offset(x = (-8).dp))
                null -> Unit
            }
        }
    }
}
