package com.example.questlog

import android.app.Application
import com.example.questlog.billing.BillingManager
import com.example.questlog.data.InstalledAppsProvider
import com.example.questlog.data.PackageManagerAppsProvider
import com.example.questlog.ui.dashboard.DashboardViewModel
import com.questlog.di.platformModule
import com.questlog.domain.PremiumStatusProvider
import com.questlog.di.sharedModule
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { BillingManager() }
    single<PremiumStatusProvider> { PremiumStatusProvider { get<BillingManager>().isPremium.value } }
    single<InstalledAppsProvider> { PackageManagerAppsProvider(get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get()) }
}

class QuestLogApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Koin DI ──────────────────────────────────────────────────────────
        startKoin {
            androidContext(this@QuestLogApp)
            modules(platformModule, sharedModule, appModule)
        }

        // ── RevenueCat ───────────────────────────────────────────────────────
        try {
            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
            Purchases.configure(
                PurchasesConfiguration.Builder(
                    context = this,
                    apiKey = BuildConfig.REVENUECAT_API_KEY,
                ).build()
            )
        } catch (_: Exception) {
            // Local fallback / preview mode
        }
    }
}
