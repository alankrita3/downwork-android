package com.raviga.downwork.data.repo

import android.app.Activity
import com.raviga.downwork.data.api.CreditPack
import com.raviga.downwork.data.api.CreditsResponse
import com.raviga.downwork.data.api.DownWorkApi
import com.raviga.downwork.data.api.apiCall
import com.raviga.downwork.data.billing.BillingManager
import com.raviga.downwork.data.billing.PurchaseOutcome
import com.raviga.downwork.data.demo.DemoApi
import com.raviga.downwork.data.local.CacheStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/** Balance and ledger, plus the purchase path through RevenueCat (section 13). */
class CreditsRepository(
    private val api: DownWorkApi,
    private val json: Json,
    private val cache: CacheStore,
    private val billing: BillingManager,
) {
    private val _credits = MutableStateFlow<CreditsResponse?>(null)
    val credits: StateFlow<CreditsResponse?> = _credits.asStateFlow()

    /** Store-formatted prices by product id, once RevenueCat has them. */
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    val isDemoBilling: Boolean get() = api is DemoApi && !billing.isAvailable

    suspend fun warmFromCache() {
        cache.read(CacheStore.CREDITS, CreditsResponse.serializer())?.let { _credits.value = it }
    }

    suspend fun refresh(): CreditsResponse {
        val response = apiCall(json) { api.credits() }
        _credits.value = response
        cache.write(CacheStore.CREDITS, CreditsResponse.serializer(), response)
        return response
    }

    /** Asks the backend to reconcile with RevenueCat, then reloads the ledger. */
    suspend fun sync(): Int {
        apiCall(json) { api.syncCredits(UUID.randomUUID().toString()) }
        return refresh().balance
    }

    suspend fun loadPrices(packs: List<CreditPack>) {
        if (!billing.isAvailable || packs.isEmpty()) return
        runCatching { billing.products(packs.map { it.productId }) }
            .onSuccess { products -> _prices.value = products.associate { it.id.substringBefore(':') to it.price.formatted } }
    }

    /**
     * Buys a pack. The webhook usually books the credits before the store
     * sheet closes; if the balance has not moved within 10s we ask for a sync.
     */
    suspend fun purchase(activity: Activity, pack: CreditPack): PurchaseOutcome {
        val demo = api as? DemoApi
        if (demo != null && !billing.isAvailable) {
            demo.demoPurchase(pack.productId)
            refresh()
            return PurchaseOutcome.Success
        }
        val before = _credits.value?.balance ?: 0
        val outcome = billing.purchase(activity, pack.productId)
        if (outcome is PurchaseOutcome.Success) {
            var moved = false
            repeat(5) {
                delay(2_000)
                if (runCatching { refresh().balance }.getOrNull()?.let { it != before } == true) { moved = true; return@repeat }
            }
            if (!moved) runCatching { sync() }
        }
        return outcome
    }

    suspend fun restore(): Int {
        if (billing.isAvailable) billing.restore()
        return sync()
    }
}
