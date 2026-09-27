# Mindful Unlocks (Intent Gate) — Design

**Status:** approved design, ready for planning
**Date:** 2026-09-27

## Goal

An **"Open mindfully"** flow inside QuestLog. The player picks one of their distraction
apps and types why they're opening it. A TypeSafe judgment decides whether that's a
specific, bounded task. If it is, the app gets **5 minutes of grace** that don't count
against today's detox reward; either way QuestLog then launches the app.

Free players get **one judged unlock per day**; Pro is unlimited. It gives the paywall a
concrete new perk ("Mindful Unlocks").

## Non-goals

- Intercepting launches that happen outside QuestLog (no Accessibility service, no
  overlay, no foreground service). Opening Instagram directly skips the gate — accepted.
- Storing what the player typed. The reason text goes to the proxy and is discarded.
- Streak changes. The streak keeps using raw per-day totals, as it already does with
  per-app limits.
- Evening check-in, unlock history screen, per-category stats.

## Background

- `CalculateDetoxRewardsUseCase` builds `allowances = blocked.associate { packageName to
  dailyLimitMs }` and passes them to `ScreenTimeRepository.fetchAndPersistToday`, which
  charges only `DetoxBudget.chargeableMs(usage, allowance)` per app. The streak rollover
  uses `screenTimeRepo.totalForegroundMs(day)` — raw, allowance-free.
- `BlocklistRepository.current()` / `observeBlockedApps()` give the blocked apps;
  `InstalledAppsProvider` (app) gives labels and the `<queries>` manifest entry already
  makes other apps' launch intents visible.
- DB is **version 9** (`QuestLogDatabase`), migrations in
  `commonMain/data/local/QuestLogMigrations.kt`. The unmerged Progress spec also plans
  migration 10 — whichever lands second renumbers.
- `BillingManager.isPremium` (app) is the Pro flag; `DashboardIntent.OpenPaywall` opens the
  paywall; `questlog_prefs` SharedPreferences already exists (`MilestoneOfferStore.PREFS`).
- TypeSafe API (checked against live docs): `POST https://api.typesafe.ai/v1/systemone`,
  `Authorization: Bearer <key>`, body `{state, model: "jev-latest", questions}`. Noul answer
  `{type: "noul", noul: 0..1}`; Choice answer `{type: "choice", choice, probabilities,
  confidence}`. Errors: 401, 422, 429, 529.

## Design

### 1. Proxy — `proxy/` (Cloudflare Worker)

`POST /judge`, JSON body `{ "app": "Instagram", "reason": "reply to mum about dinner" }`,
header `X-Install-Id: <uuid>`.

- Validation → `400`: `app` 1–60 chars, `reason` 1–200 chars after trim, install id present.
- Rate limit → `429`: Workers rate-limiting binding, `simple { limit = 10, period = 60 }`,
  keyed by install id. *Ceiling:* the id is client-chosen, so this limits honest clients
  and accidents, not an attacker; add real auth (e.g. Play Integrity) if abuse appears.
- One TypeSafe call. `state = { "app": app, "reason": reason }`. Questions (fixed
  server-side; the app never sends questions):
  - `purposeful` — **Noul**. Instructions: "Does `reason` describe a specific, bounded
    task to do in `app` — such as replying to a particular person, posting something
    specific, or looking up one thing — rather than open-ended browsing or passing time?"
    Criteria — true: "A concrete task with a natural end point"; false: "Browsing,
    scrolling, boredom, habit, or no clear task".
  - `category` — **Choice**. Instructions: "What is the main reason for opening `app`?"
    Criteria: `message` "Replying to or contacting a specific person or group";
    `create` "Posting or sharing something specific"; `lookup` "Finding one specific
    piece of information"; `work` "A job, school, or business task"; `boredom` "Passing
    time or nothing better to do"; `habit` "Opening it automatically, out of habit";
    `unclear` "Too vague or unrelated to tell".
- Response `200 { "purposeful": 0.91, "category": "message" }`. TypeSafe failure (any
  non-2xx or network error) → `502`. The key is the Worker secret `TYPESAFE_API_KEY`.
- Files: `proxy/src/index.js`, `proxy/wrangler.toml`, `proxy/package.json` (dev deps:
  `wrangler`, `vitest`), `proxy/test/index.test.js`. Not part of the Android CI.

### 2. Grant rule — `shared` `domain/unlock/MindfulUnlockRule`

```kotlin
object MindfulUnlockRule {
    const val GRACE_MS = 5 * 60_000L
    const val PURPOSEFUL_THRESHOLD = 0.7
    private val DRIFTING = setOf("boredom", "habit", "unclear")
    fun graceFor(purposeful: Double, category: String): Long =
        if (purposeful >= PURPOSEFUL_THRESHOLD && category !in DRIFTING) GRACE_MS else 0L
    const val FREE_UNLOCKS_PER_DAY = 1
}
```

Pure; the threshold is the one tuning knob.

### 3. Data — `mindful_unlock` table (migration 9 → 10)

Entity `MindfulUnlock(id: Long autoGenerate PK, date: String, packageName: String,
category: String, purposeful: Double, graceMs: Long, createdAt: Long)`, index on `date`.
No reason column.

`MindfulUnlockDao`: `insert`, `graceByPackageForDate(date): List<PackageGrace>`
(`SELECT packageName, SUM(graceMs) ... GROUP BY packageName`), `countForDate(date): Int`,
`observeCountForDate(date): Flow<Int>`.

`MindfulUnlockRepository(dao, clock, timeZone)` (injectable clock/tz like
`DailyQuestRepository`): `record(packageName, category, purposeful): Long` (computes and
stores `graceFor`, returns the grace granted), `graceMsToday(): Map<String, Long>`,
`countToday(): Int`, `observeCountToday(): Flow<Int>`.

Every fake DAO rule in CLAUDE.md applies (new DAO → new fake where tests need it).

### 4. Reward — `CalculateDetoxRewardsUseCase`

New constructor param `graceToday: suspend () -> Map<String, Long> = { emptyMap() }`
(wired in `SharedModule` to `MindfulUnlockRepository.graceMsToday()`). Allowances become
`dailyLimitMs + (grace[packageName] ?: 0)`. Nothing else in the use case changes — the
high-water mark, streak and quests are untouched. Grace behaves exactly like extra daily
limit: usable any time today, gone at midnight.

### 5. Judge client — `app` `unlock/IntentJudge`

```kotlin
sealed interface Verdict {
    data class Judged(val purposeful: Double, val category: String) : Verdict
    data object Unavailable : Verdict   // offline, timeout, 4xx/5xx, bad JSON
}
open class IntentJudge(baseUrl: String, installId: () -> String) {
    open suspend fun judge(appLabel: String, reason: String): Verdict
}
```

`HttpURLConnection` on `Dispatchers.IO`, 3 s connect + read timeout; body built and parsed
with `kotlinx-serialization-json` (`JsonObject`, no codegen plugin) — already in the
version catalog, added to `app` dependencies. Parsing lives in a pure
`parseVerdict(json: String): Verdict` for tests. Base URL from
`BuildConfig.UNLOCK_PROXY_URL` (env `UNLOCK_PROXY_URL` / `keystore.properties`
`unlockProxyUrl`, placeholder fallback → every call is `Unavailable`). Install id: random
UUID persisted in `questlog_prefs` key `install_id`.

### 6. UI — Unlock screen

Entry: a row **"Open an app mindfully"** on Today, below the quest ledger → new
`Screen.Unlock` in `QuestLogRoot` (back returns to Today).

`UnlockViewModel(blocklist, installedApps, judge, unlocks, isPremium: () -> Boolean)`:

| Step | UI | Action |
|---|---|---|
| Pick | List of blocked apps that are installed (label from `InstalledAppsProvider`) | select one |
| Reason | "What are you opening {App} for?" text field (200 max) + **Check** | judge |
| Checking | button shows "Checking…", disabled | — |
| Granted | "5 minutes, no charge." + **Open {App}** | launch |
| Drifting | "That sounds like drifting — it'll count today." + **Open anyway** / **Not now** | launch / back to Today |
| Unavailable | "Couldn't check right now." + **Open anyway** | launch |

- **Recording.** A `Judged` verdict is recorded immediately (`record(...)`), granted or not.
  `Unavailable` records nothing and gives no grace.
- **Free cap.** Before judging, if `!isPremium() && countToday() >= FREE_UNLOCKS_PER_DAY`,
  the ViewModel emits `OpenPaywall` instead of calling the proxy. The reason screen shows
  "1 free mindful unlock left today" / "Unlimited with Pro" under the button.
- **Launch.** `packageManager.getLaunchIntentForPackage(pkg)`; null → snackbar "Can't open
  that app." Launch happens in the composable (needs a Context); the ViewModel only emits
  a `Launch(packageName)` event.
- Paywall: `PRO_PERKS` gains `Perk("✦", "Mindful Unlocks", "Say why, get 5 minutes free — as often as you like")`.
- Colours only from `QuestLogTheme.colors`; copy has no emoji beyond the existing perk marks.

## Error handling

- Proxy down / offline / slow (>3 s) / placeholder URL → `Unavailable` → **Open anyway**,
  no grace, free unlock not consumed. Access is never blocked; grace needs a successful judgment.
- Malformed TypeSafe response inside the Worker → `502` → `Unavailable`.
- Launch intent missing (app uninstalled since blocked) → snackbar, stay on screen.

## Privacy

The typed reason and the app label leave the device only to our proxy and TypeSafe; the
proxy doesn't log bodies. Nothing typed is stored locally. The reason screen carries one
line: "Your answer is checked by AI and not saved."

## Testing

- `MindfulUnlockRuleTest` (commonTest): threshold boundary (0.69 / 0.7), each drifting
  category → 0, purposeful categories → `GRACE_MS`.
- `MindfulUnlockDaoTest` (desktopTest, in-memory Room): sums by package for a date,
  other dates excluded, count.
- `ScreenTimeMigrationTest` (desktopTest): 9 → 10 validates against exported `10.json`.
- `CalculateDetoxRewardsUseCaseTest`: grace lowers the charge for that app only; the
  streak rollover ignores grace.
- `IntentJudge` `parseVerdict` (app unit test): valid JSON, missing field, wrong types.
- `UnlockViewModelTest` (fake judge + fake repo): granted / drifting / unavailable paths,
  free cap emits paywall without calling the judge, Pro skips the cap, `Unavailable`
  records nothing.
- Worker: `proxy/test/index.test.js` with mocked `fetch` — 400 on bad input, 200 shape,
  502 on TypeSafe error.

## External setup (manual)

1. TypeSafe API key → `wrangler secret put TYPESAFE_API_KEY`.
2. `wrangler deploy` in `proxy/`; put the Worker URL in `UNLOCK_PROXY_URL` (CI secret) and
   `keystore.properties` `unlockProxyUrl` (local).
