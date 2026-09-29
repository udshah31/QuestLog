# QuestLog — Usage Access (PACKAGE_USAGE_STATS) declaration

## Does Play actually ask for a form?

Probably not. Play's **Permissions Declaration Form** is for its restricted permissions
(SMS, Call Log, background location, All files access, `QUERY_ALL_PACKAGES`, install packages,
Accessibility, VPN, exact alarms, full-screen intent, photo/video, health…). Usage Access is
a *special app access* the user grants in Android Settings and isn't on that list.

- QuestLog doesn't request `QUERY_ALL_PACKAGES`; it uses a `<queries>` launcher intent,
  which needs no declaration.
- Play decides after the first upload: if *App content → Sensitive permissions* or a release
  warning asks about it, use the text below. If nothing appears, only the Data safety form and
  privacy policy are needed.

Either way, Play's **User Data policy** still applies: the app must explain the access before
asking, use it only for the stated purpose, and match the privacy policy.

---

## Ready-to-paste text (if Play asks)

**Permission:** `android.permission.PACKAGE_USAGE_STATS` (Usage Access)

**Core functionality it enables (short):**
```
QuestLog is a digital-wellbeing app that rewards users for time they don't spend on apps they choose to limit. Usage access is how it measures that time: it reads how long each user-selected app was used today, and turns the time saved into in-app rewards (XP, gold, streaks and quests).
```

**Detailed description:**
```
The user picks the apps they want to cut back on (their "distractions") and can give each a daily allowance. With Usage Access, QuestLog reads the daily foreground time of those apps from Android's UsageStatsManager, compares it with the allowance, and awards XP and gold for the time saved. This is the app's core feature; without it the app cannot measure or reward anything.

Usage data is processed and stored only on the device, in the app's local database. It is never uploaded, shared, sold or used for advertising or analytics. QuestLog reads only usage time — not what the user does inside any app. The user is shown why the access is needed before being sent to Android Settings to grant it, and can revoke it there at any time.
```

**Why no alternative works:**
```
There is no other Android API that reports how long other apps were used. Digital Wellbeing data is not available to third-party apps, and the Accessibility API would be far more invasive.
```

**Video (if requested)** — 30–60 s screen recording, uploaded unlisted to YouTube/Drive:
1. Fresh install → Today screen → gear → **Distractions**.
2. The *"Usage access needed"* card → tap **Grant access** → Android's Usage access screen →
   enable QuestLog → back.
3. Today now shows **Reclaimed today** with real numbers.
4. Optional: Progress screen.

---

## In-app disclosure (must be shown before the Settings screen)

Previous copy in `BlocklistScreen.kt`:

> **Usage access needed** — QuestLog needs usage access to measure time in these apps.

That explained *what*, but not that the data stays on the phone. Now shipped, matching the
privacy policy:

> **Usage access needed** — QuestLog reads how long you use the apps on this list to reward the
> time you save. It stays on this phone and is never uploaded.
