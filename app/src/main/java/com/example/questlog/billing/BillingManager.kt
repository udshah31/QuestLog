package com.example.questlog.billing

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.purchaseWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The Pro package to sell, with display strings resolved from the store product. */
data class ProOffer(val pkg: Package, val priceText: String, val trialText: String?)

class BillingManager {

    companion object {
        const val ENTITLEMENT_PRO = "pro"
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // False until CustomerInfo arrives: isPremium=false before then means "unknown",
    // not "free" — the milestone trigger must not fire on it.
    private val _entitlementsKnown = MutableStateFlow(false)
    val entitlementsKnown: StateFlow<Boolean> = _entitlementsKnown.asStateFlow()

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    init {
        try {
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { customerInfo ->
                    updateEntitlements(customerInfo)
                }

            // Initial fetch from cache or server
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    updateEntitlements(customerInfo)
                }
                override fun onError(error: PurchasesError) {
                    // Offline fallback: check cached state
                }
            })
        } catch (_: Exception) {
            // Purchases not initialized (e.g. preview mode or sandbox unit test)
        }
    }

    private fun updateEntitlements(customerInfo: CustomerInfo) {
        _customerInfo.value = customerInfo
        val hasPro = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
        _isPremium.value = hasPro
        _entitlementsKnown.value = true
    }

    /** The current Offering's monthly (else first) package, or null on any failure. */
    suspend fun loadProOffer(): ProOffer? = try {
        val offering = Purchases.sharedInstance.awaitOfferings().current
        (offering?.monthly ?: offering?.availablePackages?.firstOrNull())?.let { pkg ->
            val product = pkg.product
            val price = product.price.formatted
            val period = product.period?.let { periodLabel(it.value, it.unit) }
            val trial = product.defaultOption?.freePhase?.billingPeriod
                ?.let { trialLabel(it.value, it.unit) }
            ProOffer(pkg, if (period != null) "$price / $period" else price, trial)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // not configured, offline, or no Offering set up
    }

    fun purchasePackage(
        activity: Activity,
        pkg: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (PurchasesError, Boolean) -> Unit,
    ) {
        Purchases.sharedInstance.purchaseWith(
            purchaseParams = com.revenuecat.purchases.PurchaseParams.Builder(activity, pkg).build(),
            onError = { error, userCancelled -> onError(error, userCancelled) },
            onSuccess = { _, customerInfo ->
                updateEntitlements(customerInfo)
                onSuccess(customerInfo)
            }
        )
    }

    /** Testing / Debug mock toggle for hackathon demo without real payment */
    fun setDebugPremium(enabled: Boolean) {
        _isPremium.value = enabled
        _entitlementsKnown.value = true
    }
}
