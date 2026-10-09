package com.raviga.downwork.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.di.AppEvent
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.status.StatusCopy
import com.raviga.downwork.ui.userLine
import com.raviga.downwork.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** One row on Home: a draft on this phone, or a submitted project. */
    data class Row(
        val id: String,
        val title: String,
        val line: String,
        val status: String,
        val unread: Int,
        val updatedAt: String,
        val route: String,
        val refused: Boolean = false,
    )

    /**
     * Drafts (only on this phone) and submitted projects (from the server), newest first.
     * A closed project's title was deleted server-side; this phone's copy names it if it has one.
     */
    val rows: StateFlow<List<Row>> = combine(container.drafts.drafts, container.projects.summaries) { drafts, summaries ->
        val localDrafts = drafts.values.filter { it.serverProjectId == null }.map { d ->
            Row(
                id = d.id,
                title = d.displayTitle.ifBlank { "Untitled draft" },
                line = when {
                    d.isRefused -> "We can't take this on"
                    d.quoteIsCurrent -> "Draft, quoted ${d.quote?.credits} credits"
                    d.document == null -> "Notes, no brief yet. Edited ${Time.relative(d.updatedAt)}"
                    else -> "Draft, edited ${Time.relative(d.updatedAt)}"
                },
                status = ProjectStatus.DRAFT,
                unread = 0,
                updatedAt = d.updatedAt,
                route = Routes.document(d.id),
                refused = d.isRefused,
            )
        }
        val byServerId = drafts.values.filter { it.serverProjectId != null }.associateBy { it.serverProjectId }
        val submitted = summaries.map { s ->
            Row(
                id = s.id,
                title = s.title.ifBlank { byServerId[s.id]?.displayTitle.orEmpty() }.ifBlank { "Project ${s.ref}" },
                line = StatusCopy.rowLine(s),
                status = s.status,
                unread = s.unreadComments,
                updatedAt = s.updatedAt.orEmpty(),
                route = Routes.forProject(s.id, s.status),
            )
        }
        (localDrafts + submitted).sortedByDescending { it.updatedAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loadedOnce = container.projects.loadedOnce

    val balance: StateFlow<Int> = combine(container.credits.credits, container.session.me) { credits, me ->
        credits?.balance ?: me?.credits?.balance ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val isDemo: Boolean get() = container.isDemo

    /** This phone was signed out of its old account; the empty state says how to get it back. */
    val signedOut: StateFlow<Boolean> = container.signedOut
    fun dismissSignedOut() = container.dismissSignedOut()

    init {
        refresh()
        viewModelScope.launch {
            container.events.collect { event ->
                if (event is AppEvent.ProjectUpdated) runCatching { container.projects.refreshAll() }
                if (event is AppEvent.CreditsUpdated) runCatching { container.credits.refresh() }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            container.drafts.load()
            runCatching { container.projects.refreshAll() }
                .onSuccess { _error.value = null }
                .onFailure { if (rows.value.isEmpty()) _error.value = it.userLine() }
            runCatching { container.credits.refresh() }
            _refreshing.value = false
        }
    }

    suspend fun markNotificationsAsked() = container.prefs.setNotificationsAsked()
    suspend fun notificationsAsked(): Boolean = container.prefs.current().notificationsAsked
}
