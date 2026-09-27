package com.example.questlog.billing

import android.content.SharedPreferences

/** Remembers that the 7-day-streak trial paywall has been shown — once, ever. */
open class MilestoneOfferStore(private val prefs: SharedPreferences) {
    open val shown: Boolean get() = prefs.getBoolean(KEY, false)
    open fun markShown() = prefs.edit().putBoolean(KEY, true).apply()

    companion object {
        const val PREFS = "questlog_prefs"
        private const val KEY = "milestone_trial_shown"
    }
}
