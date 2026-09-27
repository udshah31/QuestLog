package com.example.questlog.billing

import com.revenuecat.purchases.models.Period

private fun unitName(unit: Period.Unit): String? = when (unit) {
    Period.Unit.DAY -> "day"
    Period.Unit.WEEK -> "week"
    Period.Unit.MONTH -> "month"
    Period.Unit.YEAR -> "year"
    else -> null
}

private fun counted(value: Int, name: String) = "$value $name${if (value == 1) "" else "s"}"

/** Billing period for a price line: "month", "3 months". Null for an unknown unit. */
fun periodLabel(value: Int, unit: Period.Unit): String? =
    unitName(unit)?.let { if (value == 1) it else counted(value, it) }

/** Free-trial length: "7 days free", "1 week free". Null for an unknown unit. */
fun trialLabel(value: Int, unit: Period.Unit): String? =
    unitName(unit)?.let { "${counted(value, it)} free" }
