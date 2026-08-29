package com.example.questlog.ui.format

/** The reclaimed-time value split so the hero can style the hours separately. */
data class ReclaimedText(val hours: String?, val minutes: String)

/** e.g. 90 min -> ("1h", "30m"); 45 min -> (null, "45m"); 0 -> (null, "0m"). */
fun formatReclaimed(ms: Long): ReclaimedText {
    val totalMinutes = ms / 60_000L
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return ReclaimedText(
        hours = if (h > 0) "${h}h" else null,
        minutes = "${m}m",
    )
}

/** e.g. 2.0f -> "2.0×". One decimal place, U+00D7. */
fun formatMultiplier(multiplier: Float): String {
    val rounded = (multiplier * 10).toInt() / 10.0
    return "$rounded×"
}

/** The five level titles; anything past 4 is "Grandmaster of Focus". */
fun levelTitle(level: Int): String = when (level) {
    1 -> "Novice of Will"
    2 -> "Seeker of Focus"
    3 -> "Guardian of Time"
    4 -> "Knight of Discipline"
    else -> "Grandmaster of Focus"
}

/** Today's saved time as a 0..1 fraction of the daily budget. */
fun ringFraction(savedMs: Long, budgetMs: Long): Float {
    if (budgetMs <= 0L) return 0f
    return (savedMs.toFloat() / budgetMs).coerceIn(0f, 1f)
}
