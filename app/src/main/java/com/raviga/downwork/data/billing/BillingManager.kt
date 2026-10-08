package com.raviga.downwork.data.billing

import android.app.Activity
import android.app.Application
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getProductsWith
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.raviga.downwork.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

class BillingException(val error: PurchasesError) : Exception(error.message)

/**
 * RevenueCat over Google Play Billing. The backend is the ledger of record:
 * after any purchase or restore the app calls credits/sync and trusts that.
 * `app_user_id` is the backend-issued clientId so webhooks land on the right
 * ledger. Without a key (no secrets.properties yet) billing is unavailable and
 * the demo backend grants credits directly.
 */
class BillingManager(private val app: Application, private val apiKey: String) {

    val isAvailable: Boolean get() = apiKey.isNotBlank()
    private var configuredFor: String? = null

    fun configure(clientId: String) {
        if (!isAvailable || configuredFor == clientId) return
        if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(app, apiKey)
                .appUserID(clientId)
                .build(),
        )
        configuredFor = clientId
    }

    suspend fun products(productIds: List<String>): List<StoreProduct> = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.getProductsWith(
            productIds,
            ProductType.INAPP,
            onError = { if (cont.isActive) cont.resumeWithException(BillingException(it)) },
            onGetStoreProducts = { if (cont.isActive) cont.resume(it) },
        )
    }

    suspend fun purchase(activity: Activity, productId: String): PurchaseOutcome {
        val product = runCatching { products(listOf(productId)).firstOrNull() }
            .getOrElse { return PurchaseOutcome.Failed(it.message ?: "Store unavailable") }
            ?: return PurchaseOutcome.Failed("This pack is not available in your store right now.")
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchaseWith(
                PurchaseParams.Builder(activity, product).build(),
                onError = { error, userCancelled ->
                    if (!cont.isActive) return@purchaseWith
                    cont.resume(if (userCancelled) PurchaseOutcome.Cancelled else PurchaseOutcome.Failed(error.message))
                },
                onSuccess = { _, _ -> if (cont.isActive) cont.resume(PurchaseOutcome.Success) },
            )
        }
    }

    suspend fun restore(): CustomerInfo = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { if (cont.isActive) cont.resumeWithException(BillingException(it)) },
            onSuccess = { if (cont.isActive) cont.resume(it) },
        )
    }
}
