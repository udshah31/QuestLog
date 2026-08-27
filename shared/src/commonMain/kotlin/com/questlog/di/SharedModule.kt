package com.questlog.di

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.usecase.CalculateDetoxRewardsUseCase
import com.questlog.domain.usecase.DetoxMonitorFlow
import com.questlog.domain.usecase.GetDashboardStatsUseCase
import com.questlog.domain.usecase.PurchaseBuildingUseCase
import org.koin.dsl.module

/**
 * Default flagged distraction apps — can be overridden by user settings.
 */
val defaultFlaggedPackages = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",         // TikTok
    "com.snapchat.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.google.android.youtube",
    "com.facebook.katana",
)

val sharedModule = module {
    // Repositories
    single { ScreenTimeRepository(get(), get()) }
    single { CurrencyRepository(get()) }
    single { InventoryRepository(get()) }

    // Use cases
    factory {
        CalculateDetoxRewardsUseCase(
            screenTimeRepo = get(),
            currencyRepo = get(),
            flaggedPackages = defaultFlaggedPackages,
        )
    }
    factory { DetoxMonitorFlow(calculateDetoxRewards = get()) }
    factory { GetDashboardStatsUseCase(currencyRepo = get(), inventoryRepo = get()) }
    factory { PurchaseBuildingUseCase(currencyRepo = get(), inventoryRepo = get()) }
}
