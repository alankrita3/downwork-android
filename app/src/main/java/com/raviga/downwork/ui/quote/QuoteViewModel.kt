package com.raviga.downwork.ui.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.data.api.AppConfig
import com.raviga.downwork.data.api.Me
import com.raviga.downwork.data.api.Project
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuoteViewModel(private val container: AppContainer, val projectId: String) : ViewModel() {

    data class State(
        val project: Project? = null,
        val me: Me? = null,
        val config: AppConfig = AppConfig(),
        val balance: Int = 0,
        val quoting: Boolean = false,
        val progressMessage: String? = null,
        val progress: Float? = null,
        val github: String = "",
        val githubTouched: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        val submitted: Boolean = false,
    ) {
        val quote get() = project?.quote
        val isResubmit get() = project?.status == ProjectStatus.CHANGES_REQUESTED
        val alreadyCharged get() = project?.submission?.creditsCharged ?: 0
        /** Credits that will actually leave the balance now. */
        val due: Int get() = ((quote?.credits ?: 0) - if (isResubmit) alreadyCharged else 0)
        val shortBy: Int get() = (due - balance).coerceAtLeast(0)
        val needsNewQuote get() = project != null && project.quoteIsStale
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var quotedOnce = false

    init {
        viewModelScope.launch {
            container.projects.project(projectId).collect { p ->
                _state.update { it.copy(project = p) }
                if (p != null && p.quoteIsStale && p.isEditable && !quotedOnce) requestQuote()
            }
        }
        viewModelScope.launch {
            container.session.me.collect { me ->
                _state.update { s ->
                    s.copy(
                        me = me,
                        balance = container.credits.credits.value?.balance ?: me?.credits?.balance ?: s.balance,
                        github = if (!s.githubTouched && s.github.isBlank()) me?.deliveryTargets?.githubUsername.orEmpty() else s.github,
                    )
                }
            }
        }
        viewModelScope.launch { container.credits.credits.collect { c -> if (c != null) _state.update { it.copy(balance = c.balance) } } }
        viewModelScope.launch { container.session.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch {
            container.projects.warmProject(projectId)
            runCatching { container.projects.refresh(projectId) }
            runCatching { container.credits.refresh() }
            runCatching { container.session.refreshMe() }
        }
    }

    fun setGithub(value: String) = _state.update { it.copy(github = value, githubTouched = true, error = null) }

    fun requestQuote() {
        if (_state.value.quoting) return
        quotedOnce = true
        viewModelScope.launch {
            _state.update { it.copy(quoting = true, progressMessage = "Sizing the work", progress = null, error = null) }
            runCatching {
                container.projects.quote(projectId) { st ->
                    _state.update { it.copy(progressMessage = st.message ?: it.progressMessage, progress = st.progress) }
                }
            }.onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(quoting = false, progressMessage = null, progress = null) }
        }
    }

    fun submit() {
        val s = _state.value
        val quote = s.quote ?: return
        val github = s.github.trim()
        if (github.isBlank()) {
            _state.update { it.copy(error = "Enter your GitHub username so we can hand over the code.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            runCatching {
                if (s.isResubmit) {
                    if (github != s.me?.deliveryTargets?.githubUsername) container.session.setDeliveryTargets(github, null)
                    container.projects.resubmit(projectId, quote.id)
                } else {
                    container.projects.submit(projectId, quote.id, github, null)
                }
            }.onSuccess {
                runCatching { container.credits.refresh() }
                runCatching { container.session.refreshMe() }
                _state.update { it.copy(submitting = false, submitted = true) }
            }.onFailure { e ->
                val line = when ((e as? ApiException)?.code) {
                    ApiException.QUOTE_STALE -> { requestQuote(); "The brief changed. Here is a fresh quote." }
                    else -> e.userLine()
                }
                _state.update { it.copy(submitting = false, error = line) }
            }
        }
    }
}
