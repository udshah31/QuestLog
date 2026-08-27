package com.questlog.di

import com.questlog.data.local.DatabaseFactory
import com.questlog.domain.platform.ScreenTimeTracker
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    // Room database — one singleton per app process
    single { DatabaseFactory.create(androidContext()) }

    // Expose individual DAOs from the database singleton
    single { get<com.questlog.data.local.QuestLogDatabase>().screenTimeDao() }
    single { get<com.questlog.data.local.QuestLogDatabase>().currencyDao() }
    single { get<com.questlog.data.local.QuestLogDatabase>().inventoryDao() }

    // Platform-specific screen-time tracker
    single { ScreenTimeTracker(androidContext()) }
}
