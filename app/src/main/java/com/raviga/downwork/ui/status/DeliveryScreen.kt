package com.raviga.downwork.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BodyText
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.components.DwTextField
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SecondaryButton
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.components.TimelineView
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.openLink
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.util.Time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    val context = LocalContext.current
    var revisionSheet by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    ScreenScaffold(
        topBar = {
            DwTopBar(
                onBack = { nav.popBackStack() },
                actions = {
                    if (project != null) {
                        InlineAction("Brief", onClick = { nav.navigate(Routes.document(projectId)) })
                        InlineAction("Status", onClick = { nav.navigate(Routes.status(projectId)) })
                    }
                },
            )
        },
        bottomBar = {
            if (project?.status != ProjectStatus.DELIVERED) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Accept delivery", loading = state.busy, onClick = { vm.accept() })
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Request a revision", enabled = !state.busy, onClick = { revisionSheet = true })
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Dw.gutter)) }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                val delivery = project.delivery
                Column(Modifier.padding(horizontal = Dw.gutter)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (project.status) {
                            ProjectStatus.DELIVERED -> "Your code is ready."
                            ProjectStatus.ACCEPTED -> "Accepted."
                            ProjectStatus.REVISION_REQUESTED -> "Revision in progress."
                            else -> StatusCopy.detailTitle(project.status)
                        },
                        style = DwType.display, color = Ink.ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(project.title.ifBlank { "Untitled project" }, style = DwType.body, color = Ink.graphite)
                    if (project.status == ProjectStatus.ACCEPTED) {
                        Text("Accepted on ${Time.shortDate(project.history.lastOrNull { it.status == ProjectStatus.ACCEPTED }?.at ?: project.updatedAt)}", style = DwType.secondary, color = Ink.moss)
                    }
                    Spacer(Modifier.height(24.dp))
                }

                if (delivery?.repoUrl != null) {
                    DwRow(
                        title = delivery.repoUrl.removePrefix("https://"),
                        subtitle = "Open the repository",
                        onClick = { openLink(context, delivery.repoUrl) },
                    )
                }

                Column(Modifier.padding(horizontal = Dw.gutter)) {
                    Spacer(Modifier.height(16.dp))
                    val transfer = delivery?.transfer
                    val github = project.submission?.githubUsername?.ifBlank { null } ?: container.session.me.value?.deliveryTargets?.githubUsername?.ifBlank { null }
                    val transferLine = when (transfer?.status) {
                        "awaiting_target" -> "We need your GitHub username to transfer the repository."
                        "pending" -> "Transfer to ${github?.let { "@$it" } ?: "you"} is being prepared."
                        "initiated" -> "Transfer sent to ${github?.let { "@$it" } ?: "you"}. Accept it from the email GitHub sent you."
                        "accepted" -> "Transfer accepted. The repository is yours."
                        "failed" -> "The transfer failed" + (transfer.error?.let { ": $it" } ?: ".") + " Check your GitHub username and we will retry."
                        else -> null
                    }
                    if (transferLine != null) {
                        Text(transferLine, style = DwType.body, color = if (transfer?.status == "failed") Ink.brick else Ink.ink)
                        if (transfer?.status == "awaiting_target" || transfer?.status == "failed") {
                            Spacer(Modifier.height(8.dp))
                            InlineAction("Set GitHub username", onClick = { nav.navigate(Routes.DELIVERY_TARGETS) })
                        }
                    }
                    delivery?.note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(16.dp))
                        BodyText(it)
                    }
                    delivery?.aws?.let { aws ->
                        if (aws.status != "not_requested") {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                when (aws.status) {
                                    "deployed" -> aws.note.takeIf { it.isNotBlank() } ?: "Deployed to your AWS account."
                                    else -> "AWS deployment is in progress."
                                },
                                style = DwType.body, color = if (aws.status == "deployed") Ink.moss else Ink.ink,
                            )
                        }
                    }
                    if (project.status == ProjectStatus.DELIVERED && delivery?.acceptBy != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Accepted automatically on ${Time.shortDate(delivery.acceptBy)} if you do nothing.", style = DwType.caption, color = Ink.graphite)
                    }

                    if (project.revisions.requests.isNotEmpty()) {
                        Spacer(Modifier.height(Dw.sectionGap))
                        SectionHeading("Revisions")
                        Spacer(Modifier.height(12.dp))
                        project.revisions.requests.forEach { r ->
                            BodyText(r.message)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Requested ${Time.shortDate(r.createdAt)}" + (r.resolvedAt?.let { ", delivered ${Time.shortDate(it)}" } ?: ", in progress"),
                                style = DwType.caption, color = Ink.graphite,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    project.timeline?.takeIf { it.milestones.isNotEmpty() }?.let { tl ->
                        Spacer(Modifier.height(Dw.sectionGap))
                        TimelineView(tl.milestones, note = "Projects are usually delivered well ahead of this date.", estimatedDeliveryDate = tl.estimatedDeliveryDate)
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (revisionSheet && project != null) {
        val remaining = (project.revisions.included - project.revisions.used).coerceAtLeast(0)
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) revisionSheet = false },
            sheetState = sheet,
            containerColor = Ink.paper,
            dragHandle = null,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Dw.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
                Text("Request a revision", style = DwType.heading, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (remaining > 0) "$remaining of ${project.revisions.included} revision ${if (project.revisions.included == 1) "round" else "rounds"} left." else "Your included revision rounds are used up. Email us and we can add more.",
                    style = DwType.secondary, color = if (remaining > 0) Ink.graphite else Ink.amber,
                )
                Spacer(Modifier.height(16.dp))
                DwTextField(value = message, onValueChange = { message = it }, placeholder = "What should change?", minLines = 3)
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Send request", enabled = remaining > 0 && message.isNotBlank(), loading = state.busy, onClick = {
                    vm.requestRevision(message)
                    revisionSheet = false
                    message = ""
                })
            }
        }
    }
}
