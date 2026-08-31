# Progress Screen — Design

**Status:** approved design, ready for planning
**Date:** 2026-08-31

## Goal

Add a **Progress** screen — the fourth screen in the app, reached by tapping the streak
ring on Today. It surfaces the player's long-view: reclaimed time all-time, a 2×2 grid of
lifetime stats, the level track, and a 7-day chart of reclaimed time per day.

The chart and a "best day" stat need per-day reclaimed-time history, which the app does not
currently keep. This spec adds a small history table written on day rollover.

## Non-goals

- No backfill. History accrues from the day this ships; the chart shows an empty state
  until ~2 days of data exist.
- No changes to how reclaimed time, rewards, streaks, quests, or billing work.
- `LevelBar` (Today) is not touched — the Progress level block is a separate inline layout.

## Background

Relevant existing shape:

- `CalculateDetoxRewardsUseCase` runs the detox loop. On a **day rollover** (`lastDay`
  non-empty and `!= today`) it calls `currencyRepo.addLifetimeSaved(balance.awardedSavedMsToday)`
  — folding the finished day's saved time into the running all-time total. Individual days
  are **not** retained.
- `CurrencyRepository.observePlayerStats()` yields `PlayerStats`, where
  `lifetimeSavedMs = b.lifetimeSavedMs + b.awardedSavedMsToday` (finalised past + today's
  running award) and `todaySavedMs = b.awardedSavedMsToday`.
- `GetDashboardStatsUseCase` combines `observePlayerStats()` + buildings + blocklist into
  `DashboardState`. `DashboardViewModel` also derives `blockedAppCount`.
- `quest_completions` has one row per (date, questId) completed. `QuestDao` has no count query.
- DB is at **version 9**. Migrations live in `commonMain/data/local/QuestLogMigrations.kt`;
  `questLogMigrations` is the ordered array; `DatabaseFactory` (androidMain) wires it.
- DAOs are exposed on `QuestLogDatabase` and registered one-per-`single` in the platform
  Koin module (`shared/src/androidMain/.../di/PlatformModule.android.kt`).
- App Koin module is in `app/.../QuestLogApp.kt` (`viewModel { … }`).
- `enum class Screen { Today, Realm, Blocklist }` in `ui/QuestLogRoot.kt`; screen switching
  is an `AnimatedContent` keyed on `screen`, with `BackHandler(enabled = screen != Today)`.
- Colour comes from `QuestLogTheme.colors` semantic tokens only. Palette #1: `earned` is
  the sole accent (red); `inkSecondary`/`inkMuted` are the greys.

## Data layer

### Entity — `data/local/entity/DailySaved.kt`

```kotlin
@Entity(tableName = "daily_saved")
data class DailySaved(
    @PrimaryKey val date: String,   // ISO-8601 local date, e.g. "2026-08-31"
    val savedMs: Long,
)
```

### DAO — `data/local/dao/DailySavedDao.kt`

```kotlin
@Dao
interface DailySavedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DailySaved)

    @Query("SELECT * FROM daily_saved WHERE date >= :fromDate ORDER BY date ASC")
    fun observeSince(fromDate: String): Flow<List<DailySaved>>

    @Query("SELECT MAX(savedMs) FROM daily_saved")
    fun observeBestMs(): Flow<Long?>
}
```

### Database — `QuestLogDatabase.kt`

- `version = 10`
- add `DailySaved::class` to `entities`
- add `abstract fun dailySavedDao(): DailySavedDao`

### Migration — `QuestLogMigrations.kt`

```kotlin
/** v10: per-day reclaimed-time history, for the Progress chart and "best day". */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_saved` (" +
                "`date` TEXT NOT NULL, `savedMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))"
        )
    }
}
```

Append `MIGRATION_9_10` to `questLogMigrations`. The `CREATE TABLE` must match the entity's
exported `createSql` exactly (no DB-side `DEFAULT` the entity omits).

### Repository — `data/repository/DailySavedRepository.kt`

```kotlin
class DailySavedRepository(private val dao: DailySavedDao) {
    suspend fun record(date: String, savedMs: Long) = dao.upsert(DailySaved(date, savedMs))
    fun observeSince(fromDate: String): Flow<List<DailySaved>> = dao.observeSince(fromDate)
    fun observeBestMs(): Flow<Long?> = dao.observeBestMs()
}
```

### The rollover write — `CalculateDetoxRewardsUseCase`

Add a constructor lambda (matching the existing `evaluateDailyQuests` / `blockedApps`
pattern — **not** a new injected DAO, so existing test construction sites are unaffected):

```kotlin
private val recordDailySaved: suspend (date: String, savedMs: Long) -> Unit = { _, _ -> },
```

In the existing rollover branch:

```kotlin
if (!lastDay.isNullOrEmpty() && lastDay != todayKey) {
    val finishedDayMs = balance?.awardedSavedMsToday ?: 0L
    currencyRepo.addLifetimeSaved(finishedDayMs)
    recordDailySaved(lastDay, finishedDayMs)   // records even a 0 day
    streak = evaluateStreak(lastDay, today, streak, balance, premium)
    currencyRepo.setStreak(streak)
}
```

Only `lastDay` gets a row. Multi-day gaps (app not opened) leave gaps in the chart — this
is honest: those days had no tracking.

## Domain layer

### Model — `domain/model/ProgressStats.kt`

```kotlin
data class DaySaved(val date: LocalDate, val savedMs: Long, val isToday: Boolean)

data class ProgressStats(
    val streakDays: Int,
    val reclaimedAllTimeMs: Long,
    val bestDayMs: Long,
    val appsGuarded: Int,
    val questsCleared: Int,
    val level: Int,
    val xp: Long,
    val xpToNextLevel: Long,
    val last7Days: List<DaySaved>,   // exactly 7, chronological, [6] == today
)
```

### Query — `QuestDao`

```kotlin
@Query("SELECT COUNT(*) FROM quest_completions")
fun observeLifetimeCompletedCount(): Flow<Int>
```

### `GetProgressStatsUseCase` — `domain/usecase/GetProgressStatsUseCase.kt`

```kotlin
class GetProgressStatsUseCase(
    private val currencyRepo: CurrencyRepository,
    private val dailySavedRepo: DailySavedRepository,
    private val questDao: QuestDao,
    private val blocklistRepo: BlocklistRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<ProgressStats> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val windowStart = today.minus(6, DateTimeUnit.DAY)
        return combine(
            currencyRepo.observePlayerStats(),
            dailySavedRepo.observeSince(windowStart.toString()),
            dailySavedRepo.observeBestMs(),
            questDao.observeLifetimeCompletedCount(),
            blocklistRepo.observeBlockedApps(),
        ) { stats, history, bestHistorical, questCount, blocked ->
            val byDate = history.mapNotNull { row ->
                runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row.savedMs }
            }.toMap()
            val week = (0..6).map { i ->
                val d = windowStart.plus(i, DateTimeUnit.DAY)
                val isToday = d == today
                DaySaved(
                    date = d,
                    savedMs = if (isToday) stats.todaySavedMs else (byDate[d] ?: 0L),
                    isToday = isToday,
                )
            }
            ProgressStats(
                streakDays = stats.consecutiveDetoxDays,
                reclaimedAllTimeMs = stats.lifetimeSavedMs,
                bestDayMs = maxOf(bestHistorical ?: 0L, stats.todaySavedMs),
                appsGuarded = blocked.size,
                questsCleared = questCount,
                level = stats.level,
                xp = stats.xp,
                xpToNextLevel = stats.xpToNextLevel,
                last7Days = week,
            )
        }
    }
}
```

- Today's bar always uses the **live** `stats.todaySavedMs` (today has no `daily_saved` row
  until it rolls over).
- `bestDayMs` includes today's running value.
- `blocklistRepo.observeBlockedApps()` — same source `GetDashboardStatsUseCase` uses for its
  count.

## UI layer (`app`)

### `ui/progress/ProgressViewModel.kt`

Mirrors `BlocklistViewModel`:

```kotlin
data class ProgressUiState(val isLoading: Boolean = true, val stats: ProgressStats? = null)

class ProgressViewModel(getProgressStats: GetProgressStatsUseCase) : ViewModel() {
    val uiState: StateFlow<ProgressUiState> =
        getProgressStats()
            .map { ProgressUiState(isLoading = false, stats = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
```

### `ui/progress/ProgressScreen.kt`

`QuestScaffold`; header = `IconButton(QuestIcons.Back, "Back")` + `Text("Progress", screenTitle)`.
Body: `Hairline()`, then a `verticalScroll` `Column(spacedBy(QuestSpacing.lg))`:

1. **All-time hero** — `Text("Reclaimed all-time".uppercase(), label, inkMuted)`, then the
   value from `formatReclaimed(reclaimedAllTimeMs)` rendered `"$hours $minutes"` (or just
   `minutes` when `hours == null`) in `QuestType.display`, colour `inkPrimary` (roman — this
   is a total, not today's win).
2. `Hairline()`
3. **2×2 stat tiles** — two `Row`s of two `StatTile`s (`Modifier.weight(1f)`, `spacedBy(md)`):
   - `StatTile("Streak", "${streakDays}d", valueColor = earned)`
   - `StatTile("Best day", formatReclaimed(bestDayMs).let { "${it.hours ?: ""} ${it.minutes}".trim() })`
   - `StatTile("Apps guarded", "$appsGuarded")`
   - `StatTile("Quests cleared", "$questsCleared")`

   `StatTile(label, value, valueColor = inkPrimary)`: `Column`, `clip(QuestShapes.small)`,
   `border(1.dp, rule, QuestShapes.small)`, `padding(md)`, `heightIn(min = 72.dp)`; mono
   `label.uppercase()` in `caption`/`inkMuted`, `Spacer(sm)`, `value` in
   `QuestType.display.copy(fontSize = 26.sp, lineHeight = 28.sp)`.
4. `Hairline()`
5. **Level block** — a `Row(SpaceBetween)`: `Text("Level $level · ${levelTitle(level)}",
   bodySmall, inkMuted)` and `Text("${(xpProgress*100).toInt()}%", serifNumeral, inkMuted)`
   where `xpProgress = TimeConversion.xpProgress(xp)`; then a 3dp `rule`-track `Box` with an
   `inkSecondary` fill at `xpProgress`; then `Text("${xpToNextLevel - xp} XP to level
   ${level + 1}".uppercase(), caption, inkMuted)`.
6. `Hairline()`
7. **`ReclaimedChart(days = last7Days)`** — `Text("Last 7 days".uppercase(), label, inkMuted)`,
   `Spacer(md)`, then:
   - if `days.count { it.savedMs > 0 } < 2`: `Text("Your week fills in here as you reclaim
     time.", bodySmall, inkMuted)`.
   - else: `Row(fillMaxWidth, SpaceBetween, Bottom, height 72.dp)` of 7 columns. Each
     column `Column(horizontalAlignment = CenterHorizontally)`: a bar `Box`
     (`width 18.dp`, `height = (72.dp * (savedMs / maxInWindow)).coerceAtLeast(2.dp)`,
     `clip(RoundedCornerShape(2.dp))`, background `earned` if `isToday` else `inkSecondary`),
     `Spacer(xs)`, `Text(dayInitial(date.dayOfWeek), caption, inkMuted)`.
     `maxInWindow = days.maxOf { it.savedMs }.coerceAtLeast(1L)`.
   - `dayInitial`: `Mon→"M", Tue→"T", Wed→"W", Thu→"T", Fri→"F", Sat→"S", Sun→"S"`.
8. `Spacer(QuestSpacing.xxl)`

### Navigation — `ui/QuestLogRoot.kt`

- `enum class Screen { Today, Realm, Blocklist, Progress }`
- new `when` branch:
  ```kotlin
  Screen.Progress -> {
      val progressVm = koinViewModel<ProgressViewModel>()
      val progressState by progressVm.uiState.collectAsState()
      ProgressScreen(state = progressState, onBack = { screen = Screen.Today })
  }
  ```
- existing `BackHandler(enabled = screen != Screen.Today)` and the `AnimatedContent`
  non-Today transition already cover it — no change.

### Entry point — `ui/today/TodayScreen.kt` + `ui/today/TodayHero.kt`

- `TodayScreen` gains `onOpenProgress: () -> Unit`, forwarded to `TodayHero(state.stats, onOpenProgress)`.
- `TodayHero(stats, onOpenProgress)` — the `ProgressRing`'s modifier gets
  `.clickable(onClickLabel = "Open progress", onClick = onOpenProgress)` placed **before**
  `.clearAndSetSemantics { … }`, so the ring stays a single a11y node carrying both the
  reclaimed description and the click action.
- `QuestLogRoot` wires `onOpenProgress = { screen = Screen.Progress }` into `TodayScreen`.
- Every existing `TodayScreen(...)` / `TodayHero(...)` call site (previews, tests) adds the
  new lambda.

## DI

`shared/.../di/SharedModule.kt`:
```kotlin
single { DailySavedRepository(get()) }
factory {
    GetProgressStatsUseCase(
        currencyRepo = get(), dailySavedRepo = get(), questDao = get(), blocklistRepo = get(),
    )
}
```
and in the `CalculateDetoxRewardsUseCase` factory add:
```kotlin
recordDailySaved = { date, ms -> get<DailySavedRepository>().record(date, ms) },
```

`shared/.../di/PlatformModule.android.kt` (the only platform Koin module — desktop is
test-only and constructs directly):
```kotlin
single { get<QuestLogDatabase>().dailySavedDao() }
```
`questDao()` is already registered.

`app/.../QuestLogApp.kt`:
```kotlin
viewModel { ProgressViewModel(get()) }
```

## Testing

| Test | Module / dir | Asserts |
|---|---|---|
| `DailySavedDaoTest` | `shared` `desktopTest` (real in-memory Room, `BundledSQLiteDriver`) | `upsert` replaces on same `date`; `observeSince` is `>=` and ordered ascending; `observeBestMs` returns MAX, and `null` on an empty table |
| `ScreenTimeMigrationTest` — `9 to 10 creates daily_saved` | `shared` `desktopTest` (`MigrationTestHelper`) | table exists after `MIGRATION_9_10` and validates against `10.json`; `date` is the primary key (duplicate insert rejected) |
| `CalculateDetoxRewardsUseCaseTest` — new case | `shared` `commonTest` | on rollover, `recordDailySaved` is invoked exactly once with `(lastDay, awardedSavedMsToday)`; it is **not** invoked when `lastDay == today` (first run of the day only) |
| `GetProgressStatsUseCaseTest` | `shared` `commonTest` (hand fakes for `DailySavedDao` reads via a fake `DailySavedRepository`, a stub `QuestDao` count flow, `CurrencyRepository` over a stub `CurrencyDao`, `BlocklistRepository` over a stub `BlocklistDao`; inject fixed `clock`/`timeZone`) | `last7Days` has exactly 7 entries, `[6].isToday`, dates contiguous ending today; today's `savedMs` == `PlayerStats.todaySavedMs` even if a `daily_saved` row exists for today; a day with no row → `savedMs == 0`; `bestDayMs == maxOf(historyMax, todaySavedMs)` |
| `ProgressViewModelTest` | `app` (`@OptIn(ExperimentalCoroutinesApi::class)`, `Dispatchers.setMain(StandardTestDispatcher())`) | initial `isLoading == true`; after `advanceUntilIdle()`, `isLoading == false` and `stats` is the use case's emission |

Previews: `ProgressScreenPreview` (populated) and `ProgressScreenPreview_EmptyWeek`.

**Schema JSON:** the build regenerates `1.json … 10.json`. Commit only `10.json`;
`git checkout shared/schemas/com.questlog.data.local.QuestLogDatabase/{1..9}.json`.

**Fake fan-out:** because `recordDailySaved` has a `{ _, _ -> }` default, the existing
`CalculateDetoxRewardsUseCase(...)` construction sites in `CalculateDetoxRewardsUseCaseTest`
(8, via a helper), `DashboardViewModelTest` (4), and any in `EvaluateDailyQuestsUseCaseTest`
compile unchanged and the write is a no-op there. Only the one new rollover test passes a
capturing lambda.

## Build / verify

- `./gradlew :shared:desktopTest` — DAO, migration, use-case tests
- `./gradlew :app:testDebugUnitTest` — `ProgressViewModelTest`, existing suite
- `./gradlew :app:assembleDebug` — Android compile
- `./gradlew :app:compileDebugAndroidTestKotlin` — keep instrumented tests compiling
- Manual: emulator — tap the streak ring on Today → Progress opens; back returns; chart
  shows the empty state on a fresh install.
