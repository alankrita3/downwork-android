package com.raviga.downwork.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.di.AppEvent
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val summaries = container.projects.summaries
    val loadedOnce = container.projects.loadedOnce

    val balance: StateFlow<Int> = combine(container.credits.credits, container.session.me) { credits, me ->
        credits?.balance ?: me?.credits?.balance ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val isDemo: Boolean get() = container.isDemo

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
            runCatching { container.projects.refreshAll() }
                .onSuccess { _error.value = null }
                .onFailure { if (summaries.value.isEmpty()) _error.value = it.userLine() }
            runCatching { container.credits.refresh() }
            _refreshing.value = false
        }
    }

    suspend fun markNotificationsAsked() = container.prefs.setNotificationsAsked()
    suspend fun notificationsAsked(): Boolean = container.prefs.current().notificationsAsked
}
