# CLAUDE.md

QuestLog — gamified digital-detox app. KMP `shared` module + Compose Android `app`.
See `README.md` for architecture.

## Build & test

- `./gradlew :shared:desktopTest` — KMP logic tests on the JVM (fast, no emulator). Most tests live here.
- `./gradlew :app:testDebugUnitTest` — Android-module unit tests.
- `./gradlew :app:assembleDebug` — check Android compilation.
- Use `--no-daemon`; add `--rerun-tasks` to force test re-run (Gradle caches passing tests).
- Results: `shared/build/test-results/desktopTest/*.xml`, `app/build/test-results/testDebugUnitTest/*.xml`.
- **CI (`ci.yml`) runs only those three tasks.** Tests under `shared/src/androidUnitTest` do NOT run in CI — put shared tests in `commonTest` (runs on desktop) or `desktopTest`.
- Room exports schema JSON on build to `shared/schemas/com.questlog.data.local.QuestLogDatabase/N.json` — commit it. `--rerun-tasks` rewrites *every* `N.json` from the current entities (old versions get the new structure); commit only the newest and `git checkout` the rest to keep them frozen.

## Conventions

- `shared` package root is `com.questlog`; `app` is `com.example.questlog` (intentional; applicationId is `com.questlog.app`).
- Repositories are the write chokepoint; UI reads reactively via `GetDashboardStatsUseCase` combining flows — Room writes propagate to the UI automatically, no manual refresh needed.
- Pure, dependency-free logic goes in `util/TimeConversion` (fully unit-tested).
- `app` UI: colour comes from `QuestLogTheme.colors` (semantic tokens in `theme/QuestColors.kt`) or `MaterialTheme.colorScheme`, never a raw `Color(...)`. Two screens (`ui/today`, `ui/realm`) hosted by `ui/QuestLogRoot.kt`; no nav library. Display face *Instrument Serif* is bundled in `res/font/`.
- One shipped colour scheme — "Palette #1": charcoal ink on paper (`#FAF7FF`), `earned` (`#D72323`) is the *only* accent. `questDarkColors = questLightColors` and `QuestLogTheme` ignores its `darkTheme` param (that param + `schemeFor`'s dark branch are dormant plumbing — don't re-add a dark palette without a design; `theme/PaletteTest.kt` guards it). `currency` renders grey (palette has no gold), `locked == earned` (red). Because the theme is light-only, `MainActivity` force-pins `SystemBarStyle.light` (dark icons) and `res/values/themes.xml` sets a paper `windowBackground` (`@color/quest_window_background`).
- DB migrations live in `commonMain` (`data/local/QuestLogMigrations.kt`); `DatabaseFactory` (androidMain) wires `*questLogMigrations`. A migration's `CREATE TABLE` must match the entity's exported `createSql` exactly — put column defaults in `@ColumnInfo(defaultValue = ...)`, not just a Kotlin default; `runMigrationsAndValidate` won't flag a DB-side default the entity omits.

## Testing patterns

- Use-case / repo tests use hand-written fake DAOs that model real Room semantics (e.g. `UPDATE ... WHERE id = 1` is a no-op when the row is absent). Adding a `@Dao` method means updating every fake: `FakeScreenTimeDao` in `CalculateDetoxRewardsUseCaseTest` (shared; also used by `EvaluateDailyQuestsUseCaseTest`), the fakes in `ScreenTimeRepositoryTest`, and `app`'s `DashboardViewModelTest`.
- Real Room DB tests run on `desktop`: `Room.inMemoryDatabaseBuilder<QuestLogDatabase>().setDriver(BundledSQLiteDriver())` (needs `@ConstructedBy` on `@Database`, already present).
- Migration tests: `MigrationTestHelper` as a plain JVM test in `desktopTest`; schema dir is passed via the `questlog.schemasDir` system property set in `shared/build.gradle.kts`.
- `ScreenTimeRepository`, `DetoxMonitorFlow`, and `ScreenTimeTracker` (`expect` + both `actual`s) are `open` so tests can stub them — a real `DetoxMonitorFlow` in a `runTest` + `advanceUntilIdle()` hangs (infinite `while(true){ delay() }`).
- `app` ViewModel tests: `@OptIn(ExperimentalCoroutinesApi::class)` on the class + `Dispatchers.setMain(StandardTestDispatcher())` before `runTest` (see `DashboardViewModelTest`, `BlocklistViewModelTest`).
- Daily quests rotate: 3 of an 8-quest pool are active per day via `questsForDay(date)` (sliding window, `epochDays mod 8`). Quest tests derive the test date from the window they need (`dateWithWindow(...)` helper in `EvaluateDailyQuestsUseCaseTest`) rather than hardcoding one. `DailyQuestRepository` takes an injectable `clock`/`timeZone`.
- `BlocklistDaoTest` builds the in-memory DB with `.addCallback(questLogSeedCallback)` to exercise the fresh-install seed.
- `app/src/androidTest` (instrumented, not in CI): use `org.junit.Assert` (`kotlin.test` isn't on that classpath); keep them compiling with `./gradlew :app:compileDebugAndroidTestKotlin`.
- Theme token edits (`theme/QuestColors.kt`) must keep `ContrastTest` green — it asserts `inkSecondary/inkMuted/earned/currency` clear 4.5:1 on `ground` and `surface` for both colour sets; `earned` `#D72323` on paper is the tight one (~4.6:1).

## Invariants / gotchas

- `currency_balance` is a single row (`id = 1`); every write path calls `CurrencyRepository.ensureInitialized()` (an `INSERT OR IGNORE`) first, or the `UPDATE` silently no-ops on a fresh install.
- The detox reward is idempotent per day — a high-water mark in `currency_balance.rewardDate` / `awardedSavedMsToday`. Never re-add the full daily total. `fetchAndPersistToday` charges `currentBlocklist ∪ screen_time_records.packagesForDate(today)`, so an app blocked at *any* tick today keeps counting until midnight — unblocking never claws the reward back.
- `screen_time_records` PK is `(packageName, date)` — one row per app per day.
- `currency_balance.xp` only ever increases (every `addRewards` xpDelta is ≥ 0) — it is also the lifetime XP total. `lifetimeSavedMs` accumulates each finalised day's saved-time on rollover; the live all-time value is `lifetimeSavedMs + awardedSavedMsToday` (`PlayerStats.lifetimeSavedMs`).
- `BuildConfig.REVENUECAT_API_KEY` falls back to a placeholder; a real key comes from the
  `REVENUECAT_API_KEY` env var (CI) or `keystore.properties` `revenueCatKey` (local).
- `build/` and `.kotlin/` are gitignored; some `.idea/*` files are intentionally tracked.
- `versionCode` is computed in `app/build.gradle.kts` from `git rev-list --count HEAD` (via
  `providers.exec`, so it stays config-cache safe); `ANDROID_VERSION_CODE` env var overrides.
  `deploy-internal.yml` needs its `fetch-depth: 0` for the count to be correct.
- The distraction list is the `blocked_app` table (`BlocklistRepository`), seeded with
  `defaultFlaggedPackages` (now in `domain/model/DefaultBlocklist.kt`) via `MIGRATION_7_8`
  and the `questLogSeedCallback` on fresh installs. The detox use cases read it live
  through a `suspend () -> List<BlockedApp>` supplier; per-app `dailyLimitMs` is an
  allowance — only overage counts (`DetoxBudget.chargeableMs`).
- The blocklist app-list needs `<queries>` in `app/src/main/AndroidManifest.xml` — `PackageManager.queryIntentActivities` is filtered to near-nothing on API 30+ without it.
