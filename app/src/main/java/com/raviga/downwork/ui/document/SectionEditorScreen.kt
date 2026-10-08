package com.raviga.downwork.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.SectionBody
import com.raviga.downwork.data.api.SectionIds
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PlainEditor
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SecondaryButton
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SectionEditorViewModel(private val container: AppContainer, private val projectId: String, private val sectionId: String) : ViewModel() {

    data class State(
        val document: Document? = null,
        val heading: String = "",
        val hint: String? = null,
        val body: String = "",
        val saving: Boolean = false,
        val busyMessage: String? = null,
        val busyProgress: Float? = null,
        val sheet: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(State(heading = SectionIds.headings[sectionId] ?: ""))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.projects.warmProject(projectId)
            val doc = container.projects.cachedDocument(projectId) ?: runCatching { container.projects.refreshDocument(projectId) }.getOrNull()
            val section = doc?.section(sectionId)
            _state.update { it.copy(document = doc, heading = section?.heading ?: it.heading, hint = section?.hint, body = section?.body.orEmpty()) }
        }
    }

    fun setBody(text: String) = _state.update { it.copy(body = text) }

    /** Turns every line into a bullet, or strips bullets if they all have one. */
    fun toggleBullets() {
        val lines = _state.value.body.lines()
        val content = lines.filter { it.isNotBlank() }
        val allBullets = content.isNotEmpty() && content.all { it.trimStart().startsWith("- ") }
        val next = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                allBullets -> line.trimStart().removePrefix("- ")
                line.trimStart().startsWith("- ") -> line
                else -> "- " + line.trim()
            }
        }
        _state.update { it.copy(body = next) }
    }

    fun save() {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching { container.projects.saveDocument(projectId, doc.version, null, listOf(SectionBody(sectionId, _state.value.body.trim()))) }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.userLine()) } }
        }
    }

    fun openSheet() = _state.update { it.copy(sheet = true) }
    fun closeSheet() = _state.update { it.copy(sheet = false) }

    fun regenerate(instruction: String) {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Rereading the section", busyProgress = null, error = null) }
            runCatching {
                container.projects.regenerateSection(projectId, sectionId, instruction) { st ->
                    _state.update { it.copy(busyMessage = st.message ?: it.busyMessage, busyProgress = st.progress) }
                }
            }.onSuccess { doc ->
                _state.update { it.copy(document = doc, body = doc.section(sectionId)?.body.orEmpty()) }
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busyMessage = null, busyProgress = null, sheet = false) }
        }
    }
}

@Composable
fun SectionEditorScreen(nav: NavController, projectId: String, sectionId: String) {
    val container = LocalAppContainer.current
    val vm: SectionEditorViewModel = viewModel(key = "section_${projectId}_$sectionId") { SectionEditorViewModel(container, projectId, sectionId) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) nav.popBackStack() }

    ScreenScaffold(
        topBar = {
            DwTopBar(
                onBack = { nav.popBackStack() },
                actions = { InlineAction("Bullets", onClick = { vm.toggleBullets() }) },
            )
        },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                PrimaryButton("Save", loading = state.saving, enabled = state.document != null && state.busyMessage == null, onClick = { vm.save() })
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Regenerate with AI", enabled = state.document != null && state.busyMessage == null, onClick = { vm.openSheet() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            SectionHeading(state.heading)
            Spacer(Modifier.height(16.dp))
            PlainEditor(
                value = state.body,
                onValueChange = { vm.setBody(it) },
                placeholder = state.hint ?: "Write here. Start a line with \"- \" for a bullet.",
                minLines = 10,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(8.dp))
            Text("Paragraphs and \"- \" bullets. Nothing else is needed.", style = DwType.caption, color = Ink.ash)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (state.sheet) {
        RegenerateSheet(
            heading = state.heading,
            busyMessage = state.busyMessage,
            busyProgress = state.busyProgress,
            onDismiss = { vm.closeSheet() },
            onRegenerate = { vm.regenerate(it) },
        )
    }
}
