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

/** A length of time: "1 month", "3 months". Null for an unknown unit. */
fun lengthLabel(value: Int, unit: Period.Unit): String? = unitName(unit)?.let { counted(value, it) }

/** Free-trial length: "7 days free", "1 week free". Null for an unknown unit. */
fun trialLabel(value: Int, unit: Period.Unit): String? = lengthLabel(value, unit)?.let { "$it free" }

/**
 * The line a buyer is charged by: "$4.99 / month", or with a paid intro phase
 * (`intro` = price to length) "$0.99 for 3 months, then $4.99 / month".
 */
fun priceLine(price: String, period: String?, intro: Pair<String, String>?): String {
    val full = if (period != null) "$price / $period" else price
    return if (intro != null) "${intro.first} for ${intro.second}, then $full" else full
}
