package com.questlog.di

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailyQuestRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.usecase.CalculateDetoxRewardsUseCase
import com.questlog.domain.usecase.DetoxMonitorFlow
import com.questlog.domain.usecase.EvaluateDailyQuestsUseCase
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
    single { DailyQuestRepository(get()) }

    // Use cases
    factory {
        EvaluateDailyQuestsUseCase(
            screenTimeRepo = get(),
            inventoryRepo = get(),
            currencyRepo = get(),
            questRepo = get(),
            flaggedPackages = defaultFlaggedPackages,
        )
    }
    factory {
        val quests = get<EvaluateDailyQuestsUseCase>()
        CalculateDetoxRewardsUseCase(
            screenTimeRepo = get(),
            currencyRepo = get(),
            flaggedPackages = defaultFlaggedPackages,
            evaluateDailyQuests = { quests() },
        )
    }
    factory {
        val detox = get<CalculateDetoxRewardsUseCase>()
        DetoxMonitorFlow(runDetoxCheck = { detox() })
    }
    factory { GetDashboardStatsUseCase(currencyRepo = get(), inventoryRepo = get()) }
    factory { PurchaseBuildingUseCase(currencyRepo = get(), inventoryRepo = get()) }
}
