package com.raviga.downwork.ui.credits

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.CreditPack
import com.raviga.downwork.data.api.CreditsResponse
import com.raviga.downwork.data.api.LedgerEntry
import com.raviga.downwork.data.billing.PurchaseOutcome
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.di.AppEvent
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.components.TertiaryButton
import com.raviga.downwork.ui.findActivity
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.ui.userLine
import com.raviga.downwork.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreditsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val credits: CreditsResponse? = null,
        val packs: List<CreditPack> = emptyList(),
        val prices: Map<String, String> = emptyMap(),
        val creditValueInr: Int = 1000,
        val buying: String? = null,
        val restoring: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val demoBilling: Boolean = false,
        val billingAvailable: Boolean = false,
    )

    private val _state = MutableStateFlow(State(demoBilling = container.credits.isDemoBilling, billingAvailable = container.billing.isAvailable))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.credits.credits.collect { c -> _state.update { it.copy(credits = c) } } }
        viewModelScope.launch { container.credits.prices.collect { p -> _state.update { it.copy(prices = p) } } }
        viewModelScope.launch {
            container.session.config.collect { c ->
                _state.update { it.copy(packs = c.credits.packs, creditValueInr = c.credits.creditValueInr) }
                container.credits.loadPrices(c.credits.packs)
            }
        }
        viewModelScope.launch { container.events.collect { if (it is AppEvent.CreditsUpdated) runCatching { container.credits.refresh() } } }
        viewModelScope.launch { runCatching { container.credits.refresh() }.onFailure { e -> if (_state.value.credits == null) _state.update { it.copy(error = e.userLine()) } } }
    }

    fun buy(activity: Activity, pack: CreditPack) {
        if (_state.value.buying != null) return
        viewModelScope.launch {
            _state.update { it.copy(buying = pack.productId, error = null, notice = null) }
            val outcome = runCatching { container.credits.purchase(activity, pack) }.getOrElse { PurchaseOutcome.Failed(it.userLine()) }
            _state.update {
                it.copy(
                    buying = null,
                    error = (outcome as? PurchaseOutcome.Failed)?.message,
                    notice = when (outcome) {
                        PurchaseOutcome.Success -> "${pack.total} credits added."
                        PurchaseOutcome.Pending -> "Payment pending. Your credits arrive as soon as your bank confirms it."
                        PurchaseOutcome.NotBookedYet -> "Payment received. Your credits will appear in a minute."
                        else -> null
                    },
                )
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(restoring = true, error = null, notice = null) }
            runCatching { container.credits.restore() }
                .onSuccess { _state.update { it.copy(notice = "Purchases checked. Balance is up to date.") } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(restoring = false) }
        }
    }
}

@Composable
fun CreditsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: CreditsViewModel = viewModel { CreditsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val balance = state.credits?.balance ?: container.session.me.value?.credits?.balance ?: 0

    ScreenScaffold(topBar = { DwTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = Dw.gutter)) {
                    Spacer(Modifier.height(8.dp))
                    Text("$balance ${if (balance == 1) "credit" else "credits"}", style = DwType.display, color = Ink.ink)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "One credit is ₹${"%,d".format(state.creditValueInr)}. Credits pay for a project when you submit it, and come back if it is cancelled or not accepted.",
                        style = DwType.body, color = Ink.graphite,
                    )
                    InlineNotice(state.error)
                    InlineNotice(state.notice, color = Ink.moss)
                    Spacer(Modifier.height(Dw.sectionGap))
                    SectionHeading("Buy credits")
                    if (!state.billingAvailable && !state.demoBilling) {
                        Spacer(Modifier.height(8.dp))
                        Text("Purchases are not set up on this build yet.", style = DwType.secondary, color = Ink.amber)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(state.packs, key = { it.productId }) { pack ->
                val price = state.prices[pack.productId]
                    ?: pack.priceHintInr?.let { "₹${"%,d".format(it)}" }
                    ?: ""
                val enabled = (state.billingAvailable || state.demoBilling) && state.buying == null
                DwRow(
                    title = pack.label.ifBlank { "${pack.credits} credits" },
                    subtitle = buildString {
                        append("${pack.credits} credits")
                        if (pack.bonusCredits > 0) append(" + ${pack.bonusCredits} bonus")
                        if (state.demoBilling) append(" · demo, no charge")
                    },
                    onClick = if (enabled) ({ context.findActivity()?.let { vm.buy(it, pack) } }) else null,
                    chevron = false,
                    trailing = {
                        Text(
                            if (state.buying == pack.productId) "…" else price,
                            style = DwType.body,
                            color = if (enabled) Ink.ink else Ink.ash,
                        )
                    },
                    titleColor = if (enabled) Ink.ink else Ink.ash,
                )
            }
            item {
                Column(Modifier.padding(horizontal = Dw.gutter)) {
                    Spacer(Modifier.height(8.dp))
                    if (state.billingAvailable) TertiaryButton(if (state.restoring) "Checking…" else "Restore purchases", enabled = !state.restoring, onClick = { vm.restore() })
                    Spacer(Modifier.height(Dw.sectionGap))
                    SectionHeading("History")
                    Spacer(Modifier.height(4.dp))
                    if (state.credits == null && state.error == null) ProgressRule(Modifier.padding(vertical = 12.dp))
                    else if (state.credits?.ledger.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("No credits yet.", style = DwType.body, color = Ink.graphite)
                    }
                }
            }
            items(state.credits?.ledger.orEmpty(), key = { it.id }) { entry -> LedgerRow(entry) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
    val title = when (entry.type) {
        "purchase" -> "Bought ${entry.ref?.productId?.removePrefix("credits_")?.let { "$it credits" } ?: "credits"}"
        "bonus" -> "Pack bonus"
        "charge" -> entry.note.ifBlank { "Project submitted" }
        "refund_project" -> entry.note.ifBlank { "Project refund" }
        "refund_store" -> "Store refund"
        "revision_charge" -> "Revision"
        else -> entry.note.ifBlank { "Adjustment" }
    }
    val signed = if (entry.credits > 0) "+${entry.credits}" else "${entry.credits}"
    DwRow(
        title = title,
        subtitle = Time.dateTime(entry.at) + "  ·  balance ${entry.balanceAfter}",
        chevron = false,
        trailing = { Text(signed, style = DwType.body, color = if (entry.credits > 0) Ink.moss else Ink.ink) },
    )
}
