package com.raviga.downwork.ui.document

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.data.api.SectionIds
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BodyText
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DestructiveButton
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SecondaryButton
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.components.SectionHint
import com.raviga.downwork.ui.capture.DescribeChooserSheet
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.status.StatusCopy
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.util.Time
import kotlinx.coroutines.delay

@Composable
fun DocumentScreen(nav: NavController, projectId: String, reveal: Boolean) {
    val container = LocalAppContainer.current
    val vm: DocumentViewModel = viewModel(key = "doc_$projectId") { DocumentViewModel(container, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val project = state.project
    val document = state.document
    val rejected = project?.isRejected == true
    // A project screening refused is frozen: nothing to edit, quote or add.
    val editable = (project?.isEditable ?: false) && !rejected
    var confirmDelete by remember { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }

    // The document reveal: sections fade in one after another, 90 ms apart, once.
    var revealed by remember { mutableStateOf(if (reveal) -1 else Int.MAX_VALUE) }
    LaunchedEffect(reveal, document?.version) {
        if (reveal && document != null && revealed < 0) {
            for (i in 0..document.sections.size) {
                revealed = i
                delay(90)
            }
            revealed = Int.MAX_VALUE
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
    }

    ScreenScaffold(
        topBar = {
            DwTopBar(
                onBack = { nav.popBackStack() },
                actions = {
                    if (document != null) InlineAction("Versions", onClick = { nav.navigate(Routes.versions(projectId)) })
                    if (project?.status == ProjectStatus.DRAFT && !rejected) InlineAction("Delete", color = Ink.brick, onClick = { confirmDelete = true })
                },
            )
        },
        bottomBar = {
            if (project == null) return@ScreenScaffold
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                when {
                    rejected -> PrimaryButton("Delete draft", onClick = { confirmDelete = true })
                    editable && document != null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(
                            if (project.status == ProjectStatus.CHANGES_REQUESTED) "Get a new quote" else "Get a quote",
                            enabled = state.busyMessage == null,
                            onClick = { nav.navigate(Routes.quote(projectId)) },
                            modifier = Modifier.weight(1.4f),
                        )
                        Spacer(Modifier.width(12.dp))
                        SecondaryButton(
                            "Add more", onClick = { chooser = true },
                            enabled = state.busyMessage == null,
                            modifier = Modifier.weight(1f), icon = Icons.Outlined.Add,
                        )
                    }
                    editable -> PrimaryButton("Write the brief", enabled = state.busyMessage == null && project.inputs.isNotEmpty(), onClick = { vm.generate() })
                    else -> PrimaryButton("View status", onClick = { nav.navigate(Routes.forProject(projectId, project.status, true)) })
                }
            }
        },
    ) { padding ->
        when {
            project == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding), onRetry = { vm.refresh() })
            project == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Dw.gutter)) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(horizontal = Dw.gutter)) {
                        if (state.busyMessage != null) {
                            ProgressRule(progress = state.busyProgress?.takeIf { it > 0f })
                            Spacer(Modifier.height(8.dp))
                            Text(state.busyMessage!!, style = DwType.caption, color = Ink.cobalt)
                            Spacer(Modifier.height(16.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                        EditableTitle(
                            title = (document?.title ?: project.title).ifBlank { "Untitled project" },
                            editable = editable,
                            onSave = { vm.saveTitle(it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusLine(project, nav)
                        state.inputNotice?.let { InlineNotice(it, color = Ink.graphite) }
                        Spacer(Modifier.height(Dw.sectionGap))
                        if (rejected) {
                            RefusedBlock(project.screening?.reason, state.supportEmail)
                            Spacer(Modifier.height(Dw.sectionGap))
                        }
                    }
                }
                if (document == null) {
                    item {
                        Column(Modifier.padding(horizontal = Dw.gutter)) {
                            SectionHeading("Your description")
                            Spacer(Modifier.height(12.dp))
                            if (project.inputs.isEmpty()) {
                                SectionHint("Nothing yet. Speak, type or upload a description.")
                                Spacer(Modifier.height(16.dp))
                                if (editable) SecondaryButton("Describe it", onClick = { chooser = true })
                            } else {
                                project.inputs.forEach { input ->
                                    if (input.purgedAt != null) SectionHint("Deleted ${Time.shortDate(input.purgedAt)}, after the project closed.")
                                    else BodyText(input.text)
                                    Spacer(Modifier.height(4.dp))
                                    Text(inputSource(input) + " " + Time.relative(input.createdAt), style = DwType.caption, color = Ink.graphite)
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                            Spacer(Modifier.height(Dw.sectionGap))
                        }
                    }
                } else {
                    itemsIndexed(document.sections, key = { _, s -> s.id }) { index, section ->
                        AnimatedVisibility(
                            visible = index < revealed,
                            enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 12 },
                        ) {
                            Column(Modifier.padding(horizontal = Dw.gutter).padding(bottom = Dw.sectionGap)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                    SectionHeading(section.heading.ifBlank { SectionIds.headings[section.id] ?: section.id }, Modifier.weight(1f))
                                    if (editable) {
                                        InlineAction("Edit", enabled = state.busyMessage == null, onClick = { nav.navigate(Routes.section(projectId, section.id)) })
                                        InlineAction("Regenerate", enabled = state.busyMessage == null, onClick = { vm.openRegenerate(section.id) })
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                if (section.body.isBlank()) SectionHint(section.hint ?: "Nothing here yet.")
                                else BodyText(section.body)
                            }
                        }
                    }
                }
                item {
                    if (project.status == ProjectStatus.DRAFT && document != null) {
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    state.sheetSection?.let { sectionId ->
        val heading = document?.section(sectionId)?.heading ?: SectionIds.headings[sectionId] ?: "section"
        RegenerateSheet(
            heading = heading,
            busyMessage = state.busyMessage,
            busyProgress = state.busyProgress,
            onDismiss = { vm.closeRegenerate() },
            onRegenerate = { vm.regenerate(sectionId, it) },
        )
    }

    if (chooser && project != null) {
        DescribeChooserSheet(
            projectTitle = (document?.title ?: project.title).ifBlank { "this project" },
            onPick = { tab ->
                chooser = false
                nav.navigate(Routes.capture(projectId, if (document != null) "append" else "new", tab))
            },
            onDismiss = { chooser = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Ink.paper,
            title = { Text("Delete this draft?", style = DwType.heading, color = Ink.ink) },
            text = { Text("The description, the brief and all its versions are deleted. This cannot be undone.", style = DwType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteDraft() }) { Text("Delete", style = DwType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = DwType.button, color = Ink.ink) } },
        )
    }
}

/** Screening refused the project: say so calmly, say nothing was charged, say where to appeal. */
@Composable
private fun RefusedBlock(reason: String?, supportEmail: String) {
    Column {
        SectionHeading("We can't take this project on")
        Spacer(Modifier.height(12.dp))
        if (!reason.isNullOrBlank()) {
            BodyText(reason)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Nothing has been charged." + if (supportEmail.isNotBlank()) " If you think this is a mistake, write to $supportEmail." else "",
            style = DwType.secondary,
            color = Ink.graphite,
        )
    }
}

private fun inputSource(input: com.raviga.downwork.data.api.ProjectInput): String = when (input.kind) {
    "voice" -> "Spoken"
    "file" -> "From ${input.fileName ?: "a document"}"
    else -> "Typed"
}.let { if (input.kind == "file") "$it," else it }

@Composable
private fun EditableTitle(title: String, editable: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(title) { mutableStateOf(title) }
    val focus = remember { FocusRequester() }
    if (editing) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = DwType.title.copy(color = Ink.ink),
            cursorBrush = SolidColor(Ink.cobalt),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { editing = false; if (text.trim() != title) onSave(text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    } else {
        Text(
            title,
            style = DwType.title,
            color = Ink.ink,
            modifier = Modifier.fillMaxWidth().then(if (editable) Modifier.clickable { editing = true } else Modifier),
        )
    }
}

@Composable
private fun StatusLine(project: com.raviga.downwork.data.api.Project, nav: NavController) {
    when {
        project.status == ProjectStatus.CHANGES_REQUESTED -> Text(
            "The team left comments. Read them, edit, then get a new quote.",
            style = DwType.secondary, color = Ink.cobalt,
            modifier = Modifier.clickable { nav.navigate(Routes.status(project.id)) },
        )
        !project.isEditable && project.submission?.submittedAt != null -> Text(
            "Submitted on ${Time.shortDate(project.submission.submittedAt)}, locked",
            style = DwType.secondary, color = Ink.graphite,
        )
        else -> Text(StatusCopy.rowLine(project), style = DwType.secondary, color = Ink.graphite)
    }
}
