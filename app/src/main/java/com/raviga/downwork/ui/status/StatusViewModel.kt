package com.raviga.downwork.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.data.api.Comment
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.di.AppEvent
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shared by the status and delivery screens: one project, its comments, the client's actions on it. */
class StatusViewModel(private val container: AppContainer, val projectId: String) : ViewModel() {

    data class State(
        val project: Project? = null,
        val comments: List<Comment> = emptyList(),
        val loading: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null,
        val reply: String = "",
        val config: com.raviga.downwork.data.api.AppConfig = com.raviga.downwork.data.api.AppConfig(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.projects.project(projectId).collect { p -> _state.update { it.copy(project = p, comments = p?.review?.comments ?: it.comments) } } }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch { container.events.collect { if (it is AppEvent.ProjectUpdated && it.projectId == projectId) refresh() } }
        viewModelScope.launch {
            container.projects.warmProject(projectId)
            refresh()
        }
        // Gentle polling while the team is working, in case push is not configured yet.
        viewModelScope.launch {
            while (isActive) {
                delay(if (container.isDemo) 5_000 else 30_000)
                val status = _state.value.project?.status ?: continue
                if (!com.raviga.downwork.data.api.ProjectStatus.isTerminal(status)) runCatching { container.projects.refresh(projectId) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { container.projects.refresh(projectId) }
                .onSuccess { _state.update { it.copy(loading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = if (it.project == null) e.userLine() else null) } }
            runCatching { container.projects.comments(projectId) }
                .onSuccess { list -> _state.update { it.copy(comments = list) } }
        }
    }

    fun setReply(text: String) = _state.update { it.copy(reply = text) }

    fun sendReply() {
        val text = _state.value.reply.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.projects.addComment(projectId, text) }
                .onSuccess { _state.update { it.copy(reply = "") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun cancel() = action { container.projects.cancel(projectId) }
    fun accept() = action { container.projects.accept(projectId) }
    fun requestRevision(message: String) = action { container.projects.requestRevision(projectId, message) }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onSuccess { runCatching { container.credits.refresh() } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }
}
