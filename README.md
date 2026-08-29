# QuestLog

A gamified digital‑detox Android app. You earn RPG rewards — XP, gold, levels, day
streaks — for **not** using distracting apps, and spend the gold building a fantasy
city. A RevenueCat "Pro" subscription unlocks premium buildings and perks.

- **Kotlin Multiplatform** core (`shared`) + **Jetpack Compose** Android UI (`app`)
- Kotlin 2.3.21 · AGP 9.0.1 · JDK 21 · `compileSdk 36` · `minSdk 26`
- Room 2.8.4 (KMP) · Koin · RevenueCat · Compose Material 3

---

## Module layout

```
QuestLog
├── shared/                 Kotlin Multiplatform — all domain + data logic
│   ├── commonMain          platform-agnostic: models, use cases, repositories, Room + migrations
│   ├── androidMain         UsageStatsManager tracker, Room builder, Koin platform module
│   └── desktopMain         no-op tracker (JVM target — exists so tests run without an emulator)
└── app/                    Android application — Compose UI only
    └── com.example.questlog  MainActivity, QuestLogRoot (Today / Realm), DashboardViewModel, BillingManager
```

The `app` UI is two screens — **Today** (streak ring, level, quest ledger, realm
summary) and **Realm** (the build grid) — plus the Pro paywall dialog, hosted by
`ui/QuestLogRoot.kt` with no navigation library. A `QuestColors` token system drives
a `QuestLogTheme` with matched light ("dawn") and dark ("nightfall") palettes; the
*Instrument Serif* display face is bundled (`app/src/main/res/font/`).

The `jvm("desktop")` target in `shared` carries no product code. It exists purely so
`commonTest` — and a real in‑memory Room database — can run on the JVM in seconds
instead of on an emulator.

## Architecture

Clean-ish layering inside `shared`, consumed by the `app` UI (Today + Realm screens,
one MVI `DashboardViewModel`).

```mermaid
flowchart TD
    UI["app · Today / Realm screens + DashboardViewModel (MVI)"]
    UC["shared · use cases"]
    REPO["shared · repositories"]
    DB[("Room · questlog.db")]
    TRACKER["ScreenTimeTracker (expect/actual)"]
    RC["RevenueCat · BillingManager"]

    UI --> UC
    UI --> REPO
    UI --> RC
    UC --> REPO
    REPO --> DB
    REPO --> TRACKER
```

| Layer | Pieces |
|---|---|
| `domain/model` | `PlayerStats`, `DetoxMetrics`, `CityTile`, `AppUsage`, `DailyQuest` |
| `domain/quest` | `QuestCatalog` — the 3 fixed quest definitions + their thresholds |
| `domain/platform` | `expect class ScreenTimeTracker` — Android: `UsageStatsManager` event API; desktop: returns empty |
| `domain/usecase` | `CalculateDetoxRewardsUseCase`, `EvaluateDailyQuestsUseCase`, `DetoxMonitorFlow`, `GetDashboardStatsUseCase`, `PurchaseBuildingUseCase` |
| `data/repository` | `ScreenTimeRepository`, `CurrencyRepository`, `InventoryRepository`, `DailyQuestRepository` |
| `data/local` | `QuestLogDatabase` (`@ConstructedBy`, bundled SQLite driver) + 4 entities / DAOs, `QuestLogMigrations`, `ItemTypeConverter` |
| `util` | `TimeConversion` (reward / level math), `DetoxBudget` (saved-time formula), `StreakFreeze` (Pro streak-freeze recharge rule) — pure, fully unit-tested |
| `di` | `sharedModule` + `platformModule` (Koin) |

## The core loop

```mermaid
flowchart LR
    MON["DetoxMonitorFlow<br/>(start + every 60s)"] --> CALC["CalculateDetoxRewardsUseCase"]
    BTN["🔄 manual refresh"] --> CALC
    CALC --> TRK["ScreenTimeTracker<br/>today's usage of flagged apps"]
    CALC --> REWARD["DetoxBudget + TimeConversion<br/>saved ms → XP / gold (high-water mark)"]
    CALC --> STREAK["day rollover?<br/>advance / reset the streak"]
    CALC --> QUESTS["EvaluateDailyQuestsUseCase<br/>auto-grant completed quests"]
    REWARD --> BAL[("currency_balance")]
    STREAK --> BAL
    QUESTS --> BAL
    BAL --> DASH["GetDashboardStatsUseCase<br/>combine(currency, buildings)"]
    DASH --> STATE["DashboardState → UI"]
```

1. `DetoxMonitorFlow` emits on start and every 60 s (a failing tick is skipped, not fatal).
   The 🔄 button triggers the same recalculation on demand.
2. **Saved time.** `DetoxBudget` computes the day's saved time as
   `min(90 min, elapsed today) − total flagged-app foreground`, floored at 0 — the part of
   the day's 90‑minute budget you didn't spend on distractions. It accrues as clean time
   passes and each minute on a flagged app burns a minute of it.
3. **Rewards.** `TimeConversion` converts saved time to **10 XP + 2 gold per minute**,
   scaled by the **streak multiplier** `1.0 + 0.10 × consecutiveDays` (capped `3.0×`).
   Levels follow a triangular curve: `xpForLevel(n) = 100 · (n−1) · n / 2`. The grant is
   **idempotent per day** — a high-water mark (`awardedSavedMsToday` / `rewardDate`) means
   only the *increase* is ever granted, so repeated polls can't inflate the balance.
4. **Streak.** On the first run of a new calendar day, every day since the last run whose
   flagged-app foreground stayed within a 60‑minute budget adds to `consecutiveDetoxDays`;
   the first day over budget resets it. Phone-free gap days count as within budget.
5. **Quests.** `EvaluateDailyQuestsUseCase` checks the 3 fixed quests against the
   freshly-persisted data and auto-grants each reward exactly once per day (see below).
6. Everything lands in `currency_balance`, and the UI updates reactively through
   `GetDashboardStatsUseCase` (`combine(currency, buildings)`) plus the ViewModel's own
   `DailyQuestRepository.observeToday()` flow.

## Daily quests

An 8-quest pool (`domain/quest/QuestCatalog`), of which **3 are active each day**. The active
set is `questsForDay(date)` — a sliding window over the catalog that advances one slot per
day, so every quest runs 3 days in every 8 and consecutive days share 2 of 3. It's keyed by
the local date, so every device shows the same quests for "today". Active quests are
evaluated on every detox tick, auto-granted once per day, and reset at midnight (completions
are keyed by date).

| Quest | Completes when | Reward |
|---|---|---|
| Digital Fasting | Instagram foreground ≤ 15 min today | 150 XP / 30 gold |
| Sanctuary Builder | any building constructed today | 200 XP / 50 gold |
| Deep Focus Shield | zero flagged-app foreground 9am–12pm local (only decidable after noon) | 300 XP / 80 gold |
| Feed Freeze | Instagram + TikTok + X ≤ 10 min combined today | 200 XP / 50 gold |
| Century Saver | 60+ min of saved time banked today | 200 XP / 40 gold |
| Budget Guardian | total flagged-app foreground ≤ 30 min today | 250 XP / 60 gold |
| Master Builder | 2+ buildings constructed today | 300 XP / 70 gold |
| Dawn Discipline | zero flagged-app foreground before 9am local (only decidable after 9am) | 150 XP / 30 gold |

## Persistence

One SQLite database, `questlog.db` (schema **v7**, migrations `1→…→7` in
`data/local/QuestLogMigrations.kt`, wired by `DatabaseFactory` in `androidMain`).

| Table | Key | Holds |
|---|---|---|
| `currency_balance` | `id = 1` (single row) | `xp`, `gold`, `gems`, `consecutiveDetoxDays`, `rewardDate`, `awardedSavedMsToday`, `streakFreezeLastUsed` |
| `screen_time_records` | `(packageName, date)` | `foregroundMs` — one row per app per day |
| `inventory_items` | `itemId` | `type`, `tier`, `isPremium`, `acquiredAt` |
| `quest_completions` | `(date, questId)` | `completedAt` — a row means the quest was completed and rewarded that day |

Exported schemas live in `shared/schemas/` and are used for migration diffing and by the
`MigrationTestHelper` tests.

## Build & test

Requires **JDK 21** and an Android SDK. Create `local.properties` with your SDK path:

```properties
sdk.dir=/path/to/Android/sdk
```

| Task | Command |
|---|---|
| Fast KMP tests (JVM) | `./gradlew :shared:desktopTest` |
| Android unit tests | `./gradlew :app:testDebugUnitTest` |
| Debug APK | `./gradlew :app:assembleDebug` |
| Release bundle | `./gradlew :app:bundleRelease` |

`.github/workflows/ci.yml` runs the two test suites plus `assembleDebug` on every push
and PR. `deploy-internal.yml` builds a signed AAB and ships it to the Play internal track
on push to `main` — see [Deploy](#deploy).

### Testing approach (~99 tests)

- **Pure logic** (`TimeConversion`, `DetoxBudget`) — exhaustively unit-tested.
- **Use cases / repositories** — hand-written fake DAOs that model real Room semantics
  (e.g. an `UPDATE … WHERE id = 1` is a no-op when the row is absent).
  `EvaluateDailyQuestsUseCase` takes an injectable `Clock` so the 9am–12pm window quest
  can be tested deterministically.
- **Real Room, on the desktop target** — `ScreenTimeDaoTest` exercises Room's own SQL
  (conflict handling, composite keys) in an in-memory database, and
  `ScreenTimeMigrationTest` runs every migration through `MigrationTestHelper`, validating
  the result against the exported schema JSON — all as plain JVM tests, no emulator.

## Configuration

- **Distraction apps**: `defaultFlaggedPackages` in `di/SharedModule.kt` (Instagram,
  TikTok, Snapchat, X, Reddit, YouTube, Facebook).
- **Budgets / thresholds** are constructor params with defaults: the 90‑minute saved-time
  budget (`ScreenTimeRepository`), the 60‑minute streak budget
  (`CalculateDetoxRewardsUseCase`), and the quest constants in `QuestCatalog`.
- **Pro perks** (active while `BillingManager.isPremium`): a 2× multiplier on detox-time
  XP + gold (stacks with the streak multiplier), and a Streak Freeze Shield that protects
  one over-budget day per 7 days (`StreakFreeze.COOLDOWN_DAYS`).
- **Usage access**: the app needs the `PACKAGE_USAGE_STATS` special permission,
  granted by the user in *Settings → Apps → Special app access → Usage access*.
- **RevenueCat**: `BuildConfig.REVENUECAT_API_KEY` falls back to a placeholder; a real key
  comes from the `REVENUECAT_API_KEY` env var (CI) or `keystore.properties` `revenueCatKey`
  (local). See [Deploy](#deploy).

## Deploy

`deploy-internal.yml` builds a signed AAB and uploads it to the Play **internal** track on
every push to `main` (also runnable from the Actions tab). Both the keystore-decode and
Play-upload steps are guarded — with no secrets configured the workflow still builds an
unsigned AAB and skips the upload.

**One-time setup:**

1. **Release keystore** — generate once, then store it somewhere safe. Losing it means you
   can never ship an update:

   ```bash
   keytool -genkeypair -v -keystore questlog-release.jks -alias questlog \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Play service account** — create one in the Google Cloud console, grant it release
   access under Play Console → *Users & permissions*, and download its JSON key.

3. **Repo secrets** — `gh secret set <NAME>` (or Settings → Secrets and variables →
   Actions):

   | Secret | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | `base64 -i questlog-release.jks` |
   | `ANDROID_KEYSTORE_PASSWORD` | keystore password from step 1 |
   | `ANDROID_KEY_ALIAS` | `questlog` |
   | `ANDROID_KEY_PASSWORD` | key password from step 1 |
   | `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | contents of the step 2 JSON file |
   | `REVENUECAT_API_KEY` | RevenueCat Android SDK key |

   For local release builds, put the same values in a gitignored `keystore.properties` at
   the repo root: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, `revenueCatKey`.

`versionCode` is derived automatically from the git commit count (strictly increasing on
this merge-commit workflow), so no manual bump is needed. Override it for a one-off with
the `ANDROID_VERSION_CODE` env var. Bump `versionName` in `app/build.gradle.kts` by hand
when you want a new user-facing version string.
