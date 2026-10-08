package com.raviga.downwork.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.userLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bootstraps the session and decides the first screen: welcome, terms or
 * home. Keeps a cached session usable offline; registration is the only step
 * that truly needs the network.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val ready: Boolean = false,
        val startRoute: String = Routes.HOME,
        val error: String? = null,
        val upgradeRequired: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { bootstrap() }

    fun bootstrap() {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val session = container.session
            session.warmFromCache()
            container.projects.warmFromCache()
            container.credits.warmFromCache()
            val prefs = container.prefs.current()
            try {
                val me = session.ensureRegistered()
                container.billing.configure(me.clientId)
                launch { runCatching { container.push.syncIfNeeded() } }
                launch { runCatching { container.projects.refreshAll() } }
                launch { runCatching { container.credits.refresh() } }
                val config = session.config.value
                val start = when {
                    !prefs.welcomeDone -> Routes.WELCOME
                    session.needsLegal(me, config) -> Routes.TERMS
                    else -> Routes.HOME
                }
                _state.update { it.copy(ready = true, startRoute = start) }
            } catch (e: ApiException) {
                if (e.code == ApiException.UPGRADE_REQUIRED) {
                    _state.update { it.copy(ready = true, upgradeRequired = true) }
                } else if (session.isRegistered && session.me.value != null) {
                    // Offline with a cached identity: carry on, reads come from cache.
                    val start = if (!prefs.welcomeDone) Routes.WELCOME else Routes.HOME
                    _state.update { it.copy(ready = true, startRoute = start) }
                } else {
                    _state.update { it.copy(error = e.userLine()) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userLine()) }
            }
        }
    }
}
