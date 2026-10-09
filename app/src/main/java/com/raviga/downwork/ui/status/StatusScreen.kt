package com.raviga.downwork.ui.status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.Comment
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BodyText
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DestructiveButton
import com.raviga.downwork.ui.components.DwTextField
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.Hairline
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.components.StatusMark
import com.raviga.downwork.ui.components.TimelineView
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.util.Time

@Composable
fun StatusScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val vm: StatusViewModel = viewModel(key = "status_$projectId") { StatusViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onVisible()
        onPauseOrDispose { vm.onHidden() }
    }
    val project = state.project
    var confirmCancel by remember { mutableStateOf(false) }

    ScreenScaffold(
        topBar = {
            DwTopBar(
                onBack = { nav.popBackStack() },
                actions = { if (project != null) InlineAction("Brief", onClick = { nav.navigate(Routes.document(projectId)) }) },
            )
        },
        bottomBar = {
            if (project == null) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when (project.status) {
                    ProjectStatus.CHANGES_REQUESTED -> {
                        PrimaryButton("Edit and resubmit", onClick = { nav.navigate(Routes.document(projectId)) })
                        Spacer(Modifier.height(4.dp))
                        DestructiveButton("Cancel project", enabled = !state.busy, onClick = { confirmCancel = true })
                    }
                    ProjectStatus.SUBMITTED -> DestructiveButton("Cancel project", enabled = !state.busy, onClick = { confirmCancel = true })
                    ProjectStatus.DELIVERED, ProjectStatus.REVISION_REQUESTED, ProjectStatus.ACCEPTED ->
                        PrimaryButton("View delivery", onClick = { nav.navigate(Routes.delivery(projectId)) })
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Dw.gutter)) }
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
                Spacer(Modifier.height(8.dp))
                Text(project.title.ifBlank { "Untitled project" }, style = DwType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusMark(project.status)
                    Spacer(Modifier.width(8.dp))
                    Text(StatusCopy.detailTitle(project.status), style = DwType.body, color = Ink.ink)
                }
                Spacer(Modifier.height(2.dp))
                Text(StatusCopy.rowLine(project), style = DwType.secondary, color = Ink.graphite)
                if (project.ref.isNotBlank()) {
                    Text("Reference ${project.ref}", style = DwType.caption, color = Ink.graphite)
                }
                if (project.status == ProjectStatus.REJECTED) {
                    project.history.lastOrNull { it.status == ProjectStatus.REJECTED }?.note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = DwType.body, color = Ink.ink)
                    }
                }

                project.timeline?.takeIf { it.milestones.isNotEmpty() }?.let { tl ->
                    Spacer(Modifier.height(Dw.sectionGap))
                    TimelineView(tl.milestones, note = "Projects are usually delivered well ahead of this date.", estimatedDeliveryDate = tl.estimatedDeliveryDate)
                }

                Spacer(Modifier.height(Dw.sectionGap))
                SectionHeading("From the team")
                Spacer(Modifier.height(12.dp))
                if (state.comments.isEmpty()) {
                    Text(
                        if (project.status == ProjectStatus.SUBMITTED) "Nothing yet. We usually reply within a working day." else "No comments.",
                        style = DwType.body, color = Ink.graphite,
                    )
                } else {
                    state.comments.forEach { CommentBlock(it) }
                }
                if (ProjectStatus.canComment(project.status)) {
                    Spacer(Modifier.height(16.dp))
                    DwTextField(value = state.reply, onValueChange = { vm.setReply(it) }, placeholder = "Reply to the team", minLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        InlineAction("Send", enabled = state.reply.isNotBlank() && !state.busy, onClick = { vm.sendReply() })
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = Ink.paper,
            title = { Text("Cancel this project?", style = DwType.heading, color = Ink.ink) },
            text = { Text("Your ${project?.submission?.creditsCharged ?: 0} credits come back to your balance and the brief unlocks as a draft.", style = DwType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel() }) { Text("Cancel project", style = DwType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it", style = DwType.button, color = Ink.ink) } },
        )
    }
}

@Composable
fun CommentBlock(comment: Comment) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        BodyText(comment.body)
        Spacer(Modifier.height(4.dp))
        Text(
            (if (comment.author == "client") "You" else "DownWork team") + ", " + Time.dateTime(comment.createdAt),
            style = DwType.caption, color = Ink.graphite,
        )
        Spacer(Modifier.height(12.dp))
        Hairline()
    }
}
