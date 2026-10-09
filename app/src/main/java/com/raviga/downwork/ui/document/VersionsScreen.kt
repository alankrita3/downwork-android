package com.raviga.downwork.ui.document

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.drafts.LocalVersion
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BodyText
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.components.SectionHint
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.ui.userLine
import com.raviga.downwork.util.Time
import kotlinx.coroutines.launch

private fun LocalVersion.line(): String {
    val what = changeSummary.ifBlank {
        when (source) {
            "draft" -> "Written by AI"
            "append" -> "Added from your notes"
            "regenerate" -> "Regenerated a section"
            "edit" -> "You edited the brief"
            "restore" -> "Restored an earlier version"
            "submitted" -> "The brief you submitted"
            else -> source
        }
    }
    return "$what, ${Time.dateTime(createdAt)}"
}

/** Every version of a brief, kept on this phone only (contract v0.6). */
@Composable
fun VersionsScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    val draft by remember(projectId) { container.drafts.draft(projectId) }.collectAsStateWithLifecycle(container.drafts.get(projectId))
    val versions = draft?.versions?.reversed().orEmpty()
    val current = draft?.current?.version

    ScreenScaffold(topBar = { DwTopBar(title = "Versions", onBack = { nav.popBackStack() }) }) { padding ->
        when {
            draft == null -> ErrorState("That draft is no longer on this phone.", Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        "Versions are kept on this phone only.",
                        style = DwType.caption, color = Ink.graphite,
                        modifier = Modifier.padding(horizontal = Dw.gutter).padding(bottom = 8.dp),
                    )
                }
                items(versions, key = { it.version }) { v ->
                    DwRow(
                        title = "Version ${v.version}" + if (v.version == current) ", current" else "",
                        subtitle = v.line(),
                        onClick = { nav.navigate(Routes.version(projectId, v.version)) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun VersionPreviewScreen(nav: NavController, projectId: String, version: Int) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val draft by remember(projectId) { container.drafts.draft(projectId) }.collectAsStateWithLifecycle(container.drafts.get(projectId))
    val project = draft?.serverProjectId?.let { container.projects.cachedProject(it) }
    val v = draft?.versions?.firstOrNull { it.version == version }
    val editable = draft != null && !draft!!.isRefused &&
        (draft!!.serverProjectId == null || project?.status == com.raviga.downwork.data.api.ProjectStatus.CHANGES_REQUESTED)
    val canRestore = editable && v != null && version != draft?.current?.version

    ScreenScaffold(
        topBar = { DwTopBar(title = "Version $version", onBack = { nav.popBackStack() }) },
        bottomBar = {
            if (canRestore) BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Restore this version", loading = restoring, onClick = {
                    restoring = true
                    scope.launch {
                        runCatching { container.drafts.restore(projectId, version) }
                            .onSuccess { nav.popBackStack(Routes.VERSIONS, inclusive = true) }
                            .onFailure { error = it.userLine() }
                        restoring = false
                    }
                })
            }
        },
    ) { padding ->
        val d = v?.document
        when {
            d == null -> ErrorState("That version is no longer on this phone.", Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(horizontal = Dw.gutter)) {
                        Spacer(Modifier.height(8.dp))
                        Text(d.title.ifBlank { "Untitled project" }, style = DwType.title, color = Ink.ink)
                        Spacer(Modifier.height(8.dp))
                        Text(v.line(), style = DwType.secondary, color = Ink.graphite)
                        Spacer(Modifier.height(Dw.sectionGap))
                    }
                }
                items(d.sections, key = { it.id }) { section ->
                    Column(Modifier.padding(horizontal = Dw.gutter).padding(bottom = Dw.sectionGap)) {
                        SectionHeading(section.heading)
                        Spacer(Modifier.height(12.dp))
                        if (section.body.isBlank()) SectionHint(section.hint ?: "Empty") else BodyText(section.body)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
