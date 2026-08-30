package com.questlog.di

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailyQuestRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.PremiumStatusProvider
import com.questlog.domain.model.defaultFlaggedPackages
import com.questlog.domain.usecase.CalculateDetoxRewardsUseCase
import com.questlog.domain.usecase.DetoxMonitorFlow
import com.questlog.domain.usecase.EvaluateDailyQuestsUseCase
import com.questlog.domain.usecase.GetDashboardStatsUseCase
import com.questlog.domain.usecase.PurchaseBuildingUseCase
import org.koin.dsl.module

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
            isPremium = { getOrNull<PremiumStatusProvider>()?.isPremium() ?: false },
        )
    }
    factory {
        val detox = get<CalculateDetoxRewardsUseCase>()
        DetoxMonitorFlow(runDetoxCheck = { detox() })
    }
    factory { GetDashboardStatsUseCase(currencyRepo = get(), inventoryRepo = get()) }
    factory { PurchaseBuildingUseCase(currencyRepo = get(), inventoryRepo = get()) }
}
