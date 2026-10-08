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
import androidx.navigation.NavController
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.VersionSummary
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

private fun VersionSummary.line(): String {
    val what = when (source) {
        "ai" -> "Written by AI"
        "append" -> changeSummary ?: "Added from your description"
        "regenerate" -> changeSummary ?: "Regenerated a section"
        "edit" -> changeSummary ?: "You edited the brief"
        "restore" -> changeSummary ?: "Restored an earlier version"
        else -> changeSummary ?: source
    }
    return "$what, ${Time.dateTime(createdAt)}"
}

@Composable
fun VersionsScreen(nav: NavController, projectId: String) {
    val container = LocalAppContainer.current
    var versions by remember { mutableStateOf<List<VersionSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(projectId) {
        runCatching { container.projects.versions(projectId) }
            .onSuccess { versions = it }
            .onFailure { error = it.userLine() }
    }
    val current = container.projects.cachedDocument(projectId)?.version

    ScreenScaffold(topBar = { DwTopBar(title = "Versions", onBack = { nav.popBackStack() }) }) { padding ->
        when {
            error != null -> ErrorState(error!!, Modifier.padding(padding))
            versions == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Dw.gutter)) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(versions!!, key = { it.version }) { v ->
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
    var doc by remember { mutableStateOf<Document?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val project = container.projects.cachedProject(projectId)
    val current = container.projects.cachedDocument(projectId)?.version
    val canRestore = project?.isEditable == true && version != current

    LaunchedEffect(projectId, version) {
        runCatching { container.projects.version(projectId, version) }
            .onSuccess { doc = it }
            .onFailure { error = it.userLine() }
    }

    ScreenScaffold(
        topBar = { DwTopBar(title = "Version $version", onBack = { nav.popBackStack() }) },
        bottomBar = {
            if (canRestore && doc != null) BottomBar {
                InlineNotice(error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Restore this version", loading = restoring, onClick = {
                    restoring = true
                    scope.launch {
                        runCatching { container.projects.restore(projectId, version) }
                            .onSuccess { nav.popBackStack(Routes.document(projectId), inclusive = false); nav.popBackStack(Routes.VERSIONS, inclusive = true) }
                            .onFailure { error = it.userLine() }
                        restoring = false
                    }
                })
            }
        },
    ) { padding ->
        val d = doc
        when {
            error != null && d == null -> ErrorState(error!!, Modifier.padding(padding))
            d == null -> Column(Modifier.fillMaxSize().padding(padding)) { ProgressRule(Modifier.padding(horizontal = Dw.gutter)) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(horizontal = Dw.gutter)) {
                        Spacer(Modifier.height(8.dp))
                        Text(d.title.ifBlank { "Untitled project" }, style = DwType.title, color = Ink.ink)
                        Spacer(Modifier.height(8.dp))
                        Text((d.changeSummary ?: d.source) + ", " + Time.dateTime(d.createdAt), style = DwType.secondary, color = Ink.graphite)
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
