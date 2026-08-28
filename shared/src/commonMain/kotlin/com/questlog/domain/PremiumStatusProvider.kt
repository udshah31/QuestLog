package com.questlog.domain

/**
 * Bridges the app-module billing state (RevenueCat `pro` entitlement) into shared use
 * cases. Implemented in the app module and provided via Koin.
 */
fun interface PremiumStatusProvider {
    fun isPremium(): Boolean
}
