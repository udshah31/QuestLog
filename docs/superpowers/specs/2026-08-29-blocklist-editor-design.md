# QuestLog — In-app Blocklist Editor (design)

**Date:** 2026-08-29
**Status:** approved design, pre-plan

## Goal

Let the user choose which apps count as distractions, and optionally give each a
daily time allowance, from inside the app. Today the distraction set is a
hardcoded `defaultFlaggedPackages` in `di/SharedModule.kt` with a
"can be overridden by user settings" comment and no UI behind it.

## Scope

In:

- A searchable list of installed apps; toggle each on/off as a distraction.
- Per-app **daily allowance** — only foreground time *beyond* the allowance is
  subtracted from reclaimed time.
- Seed the current 7 defaults on first run (migration for existing installs,
  `onCreate` for fresh) so behaviour does not regress.
- A usage-access permission banner on this screen (the first permission UI in
  the app) — the blocklist is inert without `PACKAGE_USAGE_STATS`.
- Entry via a gear icon in the Today header → a dedicated blocklist screen.

Out:

- A general Settings screen (the gear opens the blocklist directly for now).
- Notifications / enforcement when an app exceeds its limit.
- Any change to the single global `DetoxBudget.DEFAULT_DAILY_BUDGET_MS`.
- Per-app usage history / charts.

## Allowance semantics

Each blocked app has `dailyLimitMs` (0 = fully blocked = today's behaviour).
Only overage counts against the player:

```
chargeableMs(usageMs, allowanceMs) = max(0, usageMs - allowanceMs)
flaggedForegroundMs = Σ over blocked apps of chargeableMs(rawForeground(pkg), dailyLimitMs(pkg))
savedMs             = DetoxBudget.savedTimeMs(budget, elapsed, flaggedForegroundMs)   // unchanged
```

The global daily budget is untouched. `DetoxBudget.savedTimeMs` is untouched; a
new pure helper `DetoxBudget.chargeableMs` is added and unit-tested in isolation.

## Quest impact — none

- `BUDGET_GUARDIAN` reads raw persisted `screen_time_records.foregroundMs`
  (`totalForegroundMs`) — persisted values stay raw, so "all distraction apps
  under 30 min" is unaffected by allowances and now tracks the user's chosen set.
- `DEEP_FOCUS_SHIELD`, `DAWN_DISCIPLINE` — live raw `flaggedForegroundInWindow`
  queries against the package set; unaffected by allowances.
- `DIGITAL_FASTING`, `FEED_FREEZE` — target hardcoded packages, independent of
  the blocklist.

## Architecture

Chosen approach: a Room table in `shared` plus a supplier lambda into the use
cases. Rejected: DataStore (second persistence mechanism in a Room-only
codebase); keeping the blocklist in `app` (inverts the layering — `shared`
currently owns `flaggedPackages`, and `DetoxMonitorFlow`'s loop would need a
refactor to re-read per tick).

### `shared` — persistence

New table `blocked_app`:

| column | type | notes |
|---|---|---|
| `packageName` | `TEXT NOT NULL` | primary key |
| `dailyLimitMs` | `INTEGER NOT NULL DEFAULT 0` | 0 = fully blocked |

Row present = app is a distraction. Row absent = not a distraction. There is no
`enabled` column — the toggle is row existence.

- `domain/model/BlockedApp.kt` — `data class BlockedApp(val packageName: String, val dailyLimitMs: Long)`
- `domain/model/DefaultBlocklist.kt` — `val defaultFlaggedPackages: Set<String>`
  moves here from `di/SharedModule.kt` so both DI and the migration can see it.
- `data/local/entity/BlockedAppEntity.kt` — `@Entity(tableName = "blocked_app")`
- `data/local/dao/BlocklistDao.kt`
  - `fun observeAll(): Flow<List<BlockedAppEntity>>`
  - `suspend fun getAll(): List<BlockedAppEntity>`
  - `suspend fun upsert(app: BlockedAppEntity)`
  - `suspend fun delete(packageName: String)`
- `data/repository/BlocklistRepository.kt` — `open` class, `BlocklistDao` ctor arg
  - `fun observeBlockedApps(): Flow<List<BlockedApp>>`
  - `suspend fun current(): List<BlockedApp>`
  - `suspend fun setBlocked(packageName: String, blocked: Boolean)` — upsert with
    `dailyLimitMs = 0` on enable (preserving an existing limit if the row exists),
    delete on disable
  - `suspend fun setLimit(packageName: String, dailyLimitMs: Long)` — upsert;
    a limit on an unblocked app blocks it

### `shared` — database + migration

- `QuestLogDatabase` — add `BlockedAppEntity::class` to `entities`, bump
  `version` to `8`, add `abstract fun blocklistDao(): BlocklistDao`.
- `QuestLogMigrations.kt` — `MIGRATION_7_8`:
  ```sql
  CREATE TABLE `blocked_app` (`packageName` TEXT NOT NULL, `dailyLimitMs` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`packageName`));
  INSERT INTO `blocked_app` (`packageName`, `dailyLimitMs`) VALUES ('com.instagram.android', 0), ... ;  -- the 7 defaults
  ```
  append to `questLogMigrations`.
- `DatabaseFactory` (androidMain) and the desktop test database builder — add a
  `RoomDatabase.Callback` whose `onCreate` inserts the same 7 seed rows, so fresh
  installs match migrated ones.
- Commit `shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json`.

### `shared` — use-case wiring

- `CalculateDetoxRewardsUseCase` — ctor param `flaggedPackages: Set<String>` →
  `blockedApps: suspend () -> List<BlockedApp>`. `invoke()` calls it once and
  passes the list to `ScreenTimeRepository.fetchAndPersistToday`.
- `EvaluateDailyQuestsUseCase` — same param change; internally
  `blockedApps().map { it.packageName }.toSet()` for `flaggedForegroundInWindow`.
- `ScreenTimeRepository.fetchAndPersistToday(flaggedPackages: Set<String>, startOfDayMs)`
  → `fetchAndPersistToday(blockedApps: List<BlockedApp>, startOfDayMs)`:
  - persist each app's **raw** `foregroundMs` as today (unchanged)
  - `flaggedForegroundMs = blockedApps.sumOf { DetoxBudget.chargeableMs(usage(it.packageName), it.dailyLimitMs) }`
  - `flaggedForegroundInWindow` keeps its `Set<String>` signature (raw query).
- `di/SharedModule.kt` — `single { BlocklistRepository(get()) }`; both use-case
  factories pass `blockedApps = { get<BlocklistRepository>().current() }`.
- `di/platformModule` (androidMain) + the desktop test module — expose
  `get<QuestLogDatabase>().blocklistDao()`.
- No change: `DetoxMonitorFlow`, `DashboardViewModel` ctor,
  `GetDashboardStatsUseCase`, `PurchaseBuildingUseCase`.

### `app` — installed apps + permission

- `data/InstalledAppsProvider.kt` (Android, uses `PackageManager`):
  - `data class InstalledApp(val packageName: String, val label: String, val icon: Drawable?)`
  - `suspend fun launchableApps(): List<InstalledApp>` — `ACTION_MAIN` /
    `CATEGORY_LAUNCHER`, drop our own package, sort by `label`, on
    `Dispatchers.IO`.
  - Koin `single { InstalledAppsProvider(androidContext()) }`.
- Permission — expose `ScreenTimeRepository.isPermissionGranted()` through the
  new VM. "Grant access" fires `Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)`.
  Re-checked on `ON_RESUME` (`LifecycleEventEffect`) so the banner clears on
  return.

### `app` — `BlocklistViewModel`

Koin `viewModel { BlocklistViewModel(get(), get()) }` — `BlocklistRepository`,
`InstalledAppsProvider`. (`isPermissionGranted` reached via `ScreenTimeRepository`
— add `get()` for it, or a small `UsageAccess` seam; decide in the plan.)

```kotlin
data class BlocklistUiState(
    val loading: Boolean = true,
    val permissionGranted: Boolean = true,
    val rows: List<AppRow> = emptyList(),   // blocked first, then A–Z; filtered by query
    val query: String = "",
)
data class AppRow(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val blocked: Boolean,
    val dailyLimitMs: Long,                  // 0 = no limit
)
```

State = `installedApps` (one-shot) `combine` `blocklistRepo.observeBlockedApps()`.
Intents: `ToggleBlocked(pkg)`, `SetLimit(pkg, ms)`, `SetQuery(String)`,
`RecheckPermission`. Writes go straight to the repo; the Flow re-emits. On a
write, also send `DashboardIntent.Refresh` equivalent so the dashboard recalcs
immediately rather than waiting for the ~60s tick (mechanism: a shared refresh
trigger or simply letting the next monitor tick handle it — decide in the plan).

### `app` — `BlocklistScreen` + navigation

- Entry: a gear glyph (`QuestIcons.Settings`, new 24dp vector) in the Today
  header beside Refresh → `Screen.Blocklist`.
- `QuestLogRoot` — add `Blocklist` to the `Screen` enum; `BackHandler` enabled
  for `screen != Screen.Today`; same slide/fade transition as Realm; hosted in
  the `SaveableStateProvider`.
- Layout: `QuestScaffold`, header "Distractions" + back arrow. Permission banner
  (surface card, `locked` accent, "Grant access" button) shown when
  `!permissionGranted`. Search field. `LazyColumn` of rows — icon
  (`drawable.toBitmap().asImageBitmap()`), label, package name (`caption`),
  trailing `Switch`. A blocked row expands to a limit control: `Pill` chips
  **Off · 15m · 30m · 1h · Custom** (Custom reveals a number field, minutes).
  "Off" = `dailyLimitMs = 0`.
- Footer caption: "Time in these apps beyond their limit is subtracted from your
  reclaimed time."
- Light + dark `@Preview` on `BlocklistScreen` and the row, hand-built fakes.

## Testing

`commonTest` (CI, on desktop):

- `DetoxBudgetTest` — `chargeableMs`: 0 under limit, overage over, never
  negative, `allowance = 0` ⇒ full usage.
- `BlocklistRepositoryTest` — fake DAO with row-existence semantics:
  `setBlocked` insert/delete, `setLimit` upsert (and blocks an unblocked app),
  `current()` / `observeBlockedApps()` shapes.
- `CalculateDetoxRewardsUseCaseTest` — supplier of apps with mixed limits ⇒
  expected `savedMs`; empty blocklist ⇒ full budget saved.
- `EvaluateDailyQuestsUseCaseTest` — migrate fakes to the supplier param;
  `DEEP_FOCUS_SHIELD` / `DAWN_DISCIPLINE` still key off the raw package set;
  `BUDGET_GUARDIAN` unchanged by limits.

`desktopTest`:

- `MigrationTestHelper` `7 → 8` — table created, 7 seed rows at `dailyLimitMs 0`.
- Real in-memory Room — `blocklistDao` upsert/delete/observe round-trip; fresh-DB
  `onCreate` seed present.

Not in CI (present, runnable locally):

- `BlocklistScreenTest` (instrumented) — toggle flips a row; limit chip updates
  it; permission banner shows when `permissionGranted = false`.

Schema: commit `8.json`.

## Files

```
shared/src/commonMain/kotlin/com/questlog/
  domain/model/BlockedApp.kt                      CREATE
  domain/model/DefaultBlocklist.kt                CREATE (moves defaultFlaggedPackages here)
  data/local/entity/BlockedAppEntity.kt           CREATE
  data/local/dao/BlocklistDao.kt                  CREATE
  data/repository/BlocklistRepository.kt          CREATE
  data/local/QuestLogDatabase.kt                  MODIFY (entity, v8, dao accessor)
  data/local/QuestLogMigrations.kt                MODIFY (MIGRATION_7_8 + seed)
  domain/usecase/CalculateDetoxRewardsUseCase.kt  MODIFY (supplier param)
  domain/usecase/EvaluateDailyQuestsUseCase.kt    MODIFY (supplier param)
  data/repository/ScreenTimeRepository.kt         MODIFY (blockedApps + overage)
  util/DetoxBudget.kt                             MODIFY (chargeableMs helper)
  di/SharedModule.kt                              MODIFY (repo + suppliers; import moves)
shared/src/androidMain/kotlin/com/questlog/
  data/local/DatabaseFactory.kt                   MODIFY (onCreate seed callback)
  di/PlatformModule.android.kt                    MODIFY (blocklistDao)
shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json   CREATE (generated)

app/src/main/java/com/example/questlog/
  data/InstalledAppsProvider.kt                   CREATE
  ui/blocklist/BlocklistViewModel.kt              CREATE
  ui/blocklist/BlocklistScreen.kt                 CREATE
  ui/blocklist/BlocklistRow.kt                    CREATE
  ui/QuestLogRoot.kt                              MODIFY (Screen.Blocklist)
  ui/today/TodayScreen.kt                         MODIFY (gear icon + onOpenBlocklist)
  theme/QuestIcons.kt                             MODIFY (Settings glyph)
  QuestLogApp.kt                                  MODIFY (Koin: provider + viewModel)

shared/src/commonTest / desktopTest               tests per above
app/src/androidTest                               BlocklistScreenTest (not in CI)
```

## Open decisions for the plan

- Exact seam for `isPermissionGranted` in `BlocklistViewModel` (reuse
  `ScreenTimeRepository` vs a thin `UsageAccess` interface).
- Immediate dashboard recalc on edit vs waiting for the next monitor tick.
- Desktop test database builder location for the `onCreate` seed callback.
