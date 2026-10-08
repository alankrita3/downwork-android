package com.raviga.downwork.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.data.api.Document
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.data.api.SectionBody
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.di.AppEvent
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentViewModel(private val container: AppContainer, val projectId: String) : ViewModel() {

    data class State(
        val project: Project? = null,
        val document: Document? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val busyMessage: String? = null,
        val busyProgress: Float? = null,
        val sheetSection: String? = null,
        val deleted: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.projects.project(projectId).collect { p -> _state.update { it.copy(project = p) } } }
        viewModelScope.launch { container.projects.document(projectId).collect { d -> _state.update { it.copy(document = d) } } }
        viewModelScope.launch {
            container.events.collect { if (it is AppEvent.ProjectUpdated && it.projectId == projectId) refresh() }
        }
        viewModelScope.launch {
            container.projects.warmProject(projectId)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { container.projects.refreshWithDocument(projectId) }
                .onSuccess { _state.update { it.copy(loading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = if (it.project == null) e.userLine() else null) } }
        }
    }

    fun openRegenerate(sectionId: String) = _state.update { it.copy(sheetSection = sectionId) }
    fun closeRegenerate() = _state.update { it.copy(sheetSection = null) }

    fun regenerate(sectionId: String, instruction: String) {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Rereading the section", busyProgress = null, error = null) }
            runCatching {
                container.projects.regenerateSection(projectId, sectionId, instruction) { st ->
                    _state.update { it.copy(busyMessage = st.message ?: it.busyMessage, busyProgress = st.progress) }
                }
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busyMessage = null, busyProgress = null, sheetSection = null) }
        }
    }

    fun saveTitle(title: String) {
        val doc = _state.value.document
        val clean = title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching {
                if (doc != null) container.projects.saveDocument(projectId, doc.version, clean, emptyList())
                else container.projects.rename(projectId, clean)
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    fun generate() {
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = "Listening back", busyProgress = null, error = null) }
            runCatching {
                container.projects.generate(projectId) { st ->
                    _state.update { it.copy(busyMessage = st.message ?: it.busyMessage, busyProgress = st.progress) }
                }
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busyMessage = null, busyProgress = null) }
        }
    }

    fun deleteDraft() {
        viewModelScope.launch {
            runCatching { container.projects.delete(projectId) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
