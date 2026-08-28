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
- Room exports schema JSON on build to `shared/schemas/com.questlog.data.local.QuestLogDatabase/N.json` — commit it.

## Conventions

- `shared` package root is `com.questlog`; `app` is `com.example.questlog` (intentional; applicationId is `com.questlog.app`).
- Repositories are the write chokepoint; UI reads reactively via `GetDashboardStatsUseCase` combining flows — Room writes propagate to the UI automatically, no manual refresh needed.
- Pure, dependency-free logic goes in `util/TimeConversion` (fully unit-tested).
- DB migrations live in `commonMain` (`data/local/QuestLogMigrations.kt`); `DatabaseFactory` (androidMain) wires `*questLogMigrations`.

## Testing patterns

- Use-case / repo tests use hand-written fake DAOs that model real Room semantics (e.g. `UPDATE ... WHERE id = 1` is a no-op when the row is absent).
- Real Room DB tests run on `desktop`: `Room.inMemoryDatabaseBuilder<QuestLogDatabase>().setDriver(BundledSQLiteDriver())` (needs `@ConstructedBy` on `@Database`, already present).
- Migration tests: `MigrationTestHelper` as a plain JVM test in `desktopTest`; schema dir is passed via the `questlog.schemasDir` system property set in `shared/build.gradle.kts`.
- `ScreenTimeRepository` and `DetoxMonitorFlow` are `open` so tests can stub them — a real `DetoxMonitorFlow` in a `runTest` + `advanceUntilIdle()` hangs (infinite `while(true){ delay() }`).
- `DashboardViewModelTest` sets `Dispatchers.setMain(StandardTestDispatcher())` before `runTest` so they share a scheduler.

## Invariants / gotchas

- `currency_balance` is a single row (`id = 1`); every write path calls `CurrencyRepository.ensureInitialized()` (an `INSERT OR IGNORE`) first, or the `UPDATE` silently no-ops on a fresh install.
- The detox reward is idempotent per day — a high-water mark in `currency_balance.rewardDate` / `awardedSavedMsToday`. Never re-add the full daily total.
- `screen_time_records` PK is `(packageName, date)` — one row per app per day.
- `BuildConfig.REVENUECAT_API_KEY` is a placeholder string, not a real key.
- `build/` and `.kotlin/` are gitignored; some `.idea/*` files are intentionally tracked.
