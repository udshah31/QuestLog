package com.questlog.domain.model

/**
 * The distraction apps seeded on first run. After first run the list is
 * user-editable and lives in the `blocked_app` table; this set is only the
 * seed (used by the DB creation callback and the v7→v8 migration).
 */
val defaultFlaggedPackages: Set<String> = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",         // TikTok
    "com.snapchat.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.google.android.youtube",
    "com.facebook.katana",
)
