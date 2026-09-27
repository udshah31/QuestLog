# Progress Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A fourth screen, **Progress**, opened by tapping Today's streak ring: all-time reclaimed time, a 2×2 stat grid (streak, best day, apps guarded, quests cleared), the level track, and a 7-day reclaimed-time chart backed by a new `daily_saved` history table written on day rollover.

**Architecture:** `shared` gets a `daily_saved` table (migration 10→11), `DailySavedRepository`, a `recordDailySaved` lambda in `CalculateDetoxRewardsUseCase`'s existing rollover branch, a lifetime quest-count query, and a pure-ish `GetProgressStatsUseCase` that combines five flows into `ProgressStats`. `app` gets `ProgressViewModel`, `ProgressScreen` (with small pure format helpers), a `Screen.Progress` branch in `QuestLogRoot`, and a click on Today's ring.

**Tech Stack:** Kotlin Multiplatform, Room KMP, kotlinx-datetime, Koin, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-08-31-progress-screen-design.md`

## Global Constraints

- DB 10 → 11 (`MIGRATION_10_11`). The migration's `CREATE TABLE` must equal the exported `createSql` in `11.json` exactly. Commit only `11.json`; `git checkout` any older `N.json` that changes. Never `--rerun-tasks` on `shared`.
- No changes to how reclaimed time, rewards, streaks, quests or billing work. The rollover writes one extra row; nothing else in the use case changes.
- Only `lastDay` gets a `daily_saved` row on rollover, **even when its value is 0**. Multi-day gaps stay gaps. No backfill.
- Today's bar and "best day" use the **live** `PlayerStats.todaySavedMs`, never a `daily_saved` row for today.
- Chart empty state when fewer than 2 of the 7 days have `savedMs > 0`: copy `"Your week fills in here as you reclaim time."`.
- Colour only via `QuestLogTheme.colors` (`earned` = the one accent; today's bar and the streak value are `earned`, other bars `inkSecondary`, track `rule`). No raw `Color(...)`.
- Copy (verbatim): title `"Progress"`; `"Reclaimed all-time"`; tiles `"Streak"`, `"Best day"`, `"Apps guarded"`, `"Quests cleared"`; `"Last 7 days"`; `"Level $level · ${levelTitle(level)}"`; `"${xpToGo} XP to level ${level + 1}"`; ring click label `"Open progress"`.
- New `QuestDao` method ⇒ update every fake: `DailyQuestRepositoryTest`, `EvaluateDailyQuestsUseCaseTest` (shared), `DashboardViewModelTest` (app).
- CI: `:shared:desktopTest`, `:app:testDebugUnitTest`, `:app:assembleDebug` green; also `:app:compileDebugAndroidTestKotlin`. `--no-daemon` everywhere.

## Review Focus

1. **A `daily_saved` row exists for today** (clock moved back, or a rollover bug) — today's bar and best-day must still use the live value, not the row. Test in Task 3.
2. **A malformed `date` in `daily_saved`** — the row is ignored; the screen still renders 7 days. Test in Task 3.
3. **Brand-new install** — empty history table: `bestDayMs` is today's running value (not 0 when today > 0), and the chart shows the empty state. Tests in Task 3 (`bestDayMs`) and Task 4 (`showsEmptyWeek`).
4. **A finished day with 0 saved** — rollover still records a 0 row (honest "tracked, nothing saved"), and it doesn't count toward "2 days with data". Tests in Task 2 and Task 4.
5. **Exactly on a level boundary** (`xp == xpForLevel(level)`) — "XP to next level" stays positive and the bar starts at 0%. Test in Task 4 (`xpToGo`).

---

## File map

| File | Change |
|---|---|
| `shared/src/commonMain/kotlin/com/questlog/data/local/entity/DailySaved.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/data/local/dao/DailySavedDao.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogDatabase.kt` | **Modify** — entity, DAO, v11 |
| `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogMigrations.kt` | **Modify** — `MIGRATION_10_11` |
| `shared/schemas/com.questlog.data.local.QuestLogDatabase/11.json` | **Generated**, commit |
| `shared/src/androidMain/kotlin/com/questlog/di/PlatformModule.android.kt` | **Modify** |
| `shared/src/desktopTest/kotlin/com/questlog/data/local/DailySavedDaoTest.kt` | **Create** |
| `shared/src/desktopTest/kotlin/com/questlog/data/local/ScreenTimeMigrationTest.kt` | **Modify** |
| `shared/src/commonMain/kotlin/com/questlog/data/repository/DailySavedRepository.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCase.kt` | **Modify** — `recordDailySaved` |
| `shared/src/commonTest/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCaseTest.kt` | **Modify** |
| `shared/src/commonMain/kotlin/com/questlog/data/local/dao/QuestDao.kt` | **Modify** — count query |
| 3 `QuestDao` fakes (see constraints) | **Modify** |
| `shared/src/desktopTest/kotlin/com/questlog/data/local/QuestDaoCountTest.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/domain/model/ProgressStats.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/domain/usecase/GetProgressStatsUseCase.kt` | **Create** |
| `shared/src/commonTest/kotlin/com/questlog/domain/usecase/GetProgressStatsUseCaseTest.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt` | **Modify** |
| `app/src/main/java/com/example/questlog/ui/progress/ProgressFormat.kt` | **Create** — pure helpers |
| `app/src/main/java/com/example/questlog/ui/progress/ProgressViewModel.kt` | **Create** |
| `app/src/test/java/com/example/questlog/ui/progress/ProgressFormatTest.kt` | **Create** |
| `app/src/test/java/com/example/questlog/ui/progress/ProgressViewModelTest.kt` | **Create** |
| `app/src/main/java/com/example/questlog/ui/progress/ProgressScreen.kt` | **Create** |
| `app/src/main/java/com/example/questlog/ui/today/TodayHero.kt`, `TodayScreen.kt` | **Modify** |
| `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt`, `QuestLogApp.kt` | **Modify** |
| `app/src/androidTest/java/com/example/questlog/ui/TodayScreenTest.kt` | **Modify** |
| `README.md`, `CLAUDE.md` | **Modify** |

---

### Task 1: `daily_saved` table, DAO, migration 10 → 11

**Files:** Create `DailySaved.kt`, `DailySavedDao.kt`, `DailySavedDaoTest.kt`; modify `QuestLogDatabase.kt`, `QuestLogMigrations.kt`, `PlatformModule.android.kt`, `ScreenTimeMigrationTest.kt`; generated `11.json`.

**Interfaces:**
- Produces: `data class DailySaved(@PrimaryKey val date: String, val savedMs: Long)` (table `daily_saved`); `interface DailySavedDao { suspend fun upsert(row: DailySaved); fun observeSince(fromDate: String): Flow<List<DailySaved>>; fun observeBestMs(): Flow<Long?> }`; `QuestLogDatabase.dailySavedDao()`; `MIGRATION_10_11`; DB version 11.

- [ ] **Step 1: Entity, DAO, DB wiring** (the tests can't compile without them)

`shared/src/commonMain/kotlin/com/questlog/data/local/entity/DailySaved.kt`:

```kotlin
package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One finished day's reclaimed time, written on day rollover. For the Progress chart. */
@Entity(tableName = "daily_saved")
data class DailySaved(
    @PrimaryKey val date: String, // ISO-8601 local date, e.g. "2026-08-31"
    val savedMs: Long,
)
```

`shared/src/commonMain/kotlin/com/questlog/data/local/dao/DailySavedDao.kt`:

```kotlin
package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.DailySaved
import kotlinx.coroutines.flow.Flow

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

`QuestLogDatabase.kt`: add imports `com.questlog.data.local.entity.DailySaved`, `com.questlog.data.local.dao.DailySavedDao`; add `DailySaved::class` to `entities`; `version = 11`; add `abstract fun dailySavedDao(): DailySavedDao`.

`QuestLogMigrations.kt`, after `MIGRATION_9_10`:

```kotlin
/** v11: per-day reclaimed-time history, for the Progress chart and "best day". */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_saved` (" +
                "`date` TEXT NOT NULL, `savedMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))"
        )
    }
}
```

and append `MIGRATION_10_11` to `questLogMigrations` (after `MIGRATION_9_10`).

`PlatformModule.android.kt`: add `single { get<com.questlog.data.local.QuestLogDatabase>().dailySavedDao() }` after the `mindfulUnlockDao()` line.

- [ ] **Step 2: Export `11.json`, check the DDL**

Run: `./gradlew :shared:desktopTestClasses --no-daemon`
Then:

```bash
python3 -c "
import json;d=json.load(open('shared/schemas/com.questlog.data.local.QuestLogDatabase/11.json'))
print([e['createSql'] for e in d['database']['entities'] if e['tableName']=='daily_saved'])"
git status --short shared/schemas
```

Expected: `CREATE TABLE IF NOT EXISTS \`${TABLE_NAME}\` (\`date\` TEXT NOT NULL, \`savedMs\` INTEGER NOT NULL, PRIMARY KEY(\`date\`))`, and only `11.json` new. If Room's text differs, change `MIGRATION_10_11` to match it exactly. `git checkout` any other changed `N.json`.

- [ ] **Step 3: Write the tests**

`shared/src/desktopTest/kotlin/com/questlog/data/local/DailySavedDaoTest.kt`:

```kotlin
package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.DailySaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailySavedDaoTest {
    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    private val dao = db.dailySavedDao()

    @AfterTest fun tearDown() = db.close()

    @Test
    fun `upsert replaces the row for the same date`() = runTest {
        dao.upsert(DailySaved("2026-09-20", 10))
        dao.upsert(DailySaved("2026-09-20", 20))
        assertEquals(listOf(DailySaved("2026-09-20", 20)), dao.observeSince("2026-01-01").first())
    }

    @Test
    fun `observeSince is inclusive and ascending`() = runTest {
        dao.upsert(DailySaved("2026-09-22", 3))
        dao.upsert(DailySaved("2026-09-20", 1))
        dao.upsert(DailySaved("2026-09-21", 2))
        assertEquals(listOf("2026-09-21", "2026-09-22"), dao.observeSince("2026-09-21").first().map { it.date })
    }

    @Test
    fun `observeBestMs is the max, and null on an empty table`() = runTest {
        assertNull(dao.observeBestMs().first())
        dao.upsert(DailySaved("2026-09-20", 5))
        dao.upsert(DailySaved("2026-09-21", 40))
        dao.upsert(DailySaved("2026-09-22", 0))
        assertEquals(40L, dao.observeBestMs().first())
    }
}
```

In `ScreenTimeMigrationTest.kt`, after the `9 to 10` test:

```kotlin
    @Test
    fun `10 to 11 creates daily_saved keyed by date`() = runTest {
        helper.createDatabase(10).close()

        val v11 = helper.runMigrationsAndValidate(11, listOf(MIGRATION_10_11))

        v11.execSQL("INSERT INTO daily_saved (date, savedMs) VALUES ('2026-09-20', 600000)")
        val duplicate = runCatching {
            v11.execSQL("INSERT INTO daily_saved (date, savedMs) VALUES ('2026-09-20', 1)")
        }
        assertTrue(duplicate.isFailure, "date is the primary key")
        assertEquals(600_000L, v11.queryLongs("SELECT savedMs FROM daily_saved").single().single())
        v11.close()
    }
```

- [ ] **Step 4: Run**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.data.local.*"`
Expected: PASS — `DailySavedDaoTest` 3, migration test incl. `10 to 11` (the red state was the Step-2 DDL check; if `runMigrationsAndValidate` fails, fix the SQL).

- [ ] **Step 5: Commit**

```bash
git add shared/src shared/schemas/com.questlog.data.local.QuestLogDatabase/11.json
git commit -m "Progress: daily_saved table, DAO, migration 10 to 11"
```

---

### Task 2: Record each finished day on rollover

**Files:** Create `DailySavedRepository.kt`; modify `CalculateDetoxRewardsUseCase.kt`, `SharedModule.kt`, `CalculateDetoxRewardsUseCaseTest.kt`.

**Interfaces:**
- Consumes: `DailySavedDao`, `DailySaved` (Task 1).
- Produces: `class DailySavedRepository(dao: DailySavedDao) { suspend fun record(date: String, savedMs: Long); fun observeSince(fromDate: String): Flow<List<DailySaved>>; fun observeBestMs(): Flow<Long?> }`; `CalculateDetoxRewardsUseCase(..., recordDailySaved: suspend (date: String, savedMs: Long) -> Unit = { _, _ -> }, ...)` placed after `graceToday`, before `clock`.

- [ ] **Step 1: Write the failing tests** (add to `CalculateDetoxRewardsUseCaseTest`)

```kotlin
    @Test
    fun `rollover records the finished day once, even a zero day`() = runTest {
        for (finished in listOf(40 * 60_000L, 0L)) {
            val currencyDao = FakeCurrencyDao().apply {
                balance = balance.copy(rewardDate = daysAgoKey(1), awardedSavedMsToday = finished)
            }
            val recorded = mutableListOf<Pair<String, Long>>()
            val useCase = CalculateDetoxRewardsUseCase(
                StubScreenTimeRepo(savedMs = 10 * 60_000L), CurrencyRepository(currencyDao),
                blocked("com.instagram.android"),
                recordDailySaved = { d, ms -> recorded += d to ms },
            )

            useCase(); useCase() // second run is the same day: no rollover

            assertEquals(listOf(daysAgoKey(1) to finished), recorded)
        }
    }

    @Test
    fun `no rollover on the very first run records nothing`() = runTest {
        val recorded = mutableListOf<Pair<String, Long>>()
        CalculateDetoxRewardsUseCase(
            StubScreenTimeRepo(savedMs = 10 * 60_000L), CurrencyRepository(FakeCurrencyDao()),
            blocked("com.instagram.android"),
            recordDailySaved = { d, ms -> recorded += d to ms },
        )()
        assertEquals(emptyList(), recorded)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.domain.usecase.CalculateDetoxRewardsUseCaseTest"`
Expected: FAIL — `No parameter with name 'recordDailySaved'`.

- [ ] **Step 3: Implement**

`shared/src/commonMain/kotlin/com/questlog/data/repository/DailySavedRepository.kt`:

```kotlin
package com.questlog.data.repository

import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.entity.DailySaved
import kotlinx.coroutines.flow.Flow

class DailySavedRepository(private val dao: DailySavedDao) {
    suspend fun record(date: String, savedMs: Long) = dao.upsert(DailySaved(date, savedMs))
    fun observeSince(fromDate: String): Flow<List<DailySaved>> = dao.observeSince(fromDate)
    fun observeBestMs(): Flow<Long?> = dao.observeBestMs()
}
```

`CalculateDetoxRewardsUseCase.kt` — constructor, after `graceToday`:

```kotlin
    /** Called once per rollover with the finished day and its final saved time (Progress history). */
    private val recordDailySaved: suspend (date: String, savedMs: Long) -> Unit = { _, _ -> },
```

In the rollover branch replace

```kotlin
            currencyRepo.addLifetimeSaved(balance?.awardedSavedMsToday ?: 0L)
```

with

```kotlin
            val finishedDayMs = balance?.awardedSavedMsToday ?: 0L
            currencyRepo.addLifetimeSaved(finishedDayMs)
            recordDailySaved(lastDay, finishedDayMs) // records even a 0 day
```

`SharedModule.kt`: import `com.questlog.data.repository.DailySavedRepository`; add `single { DailySavedRepository(get()) }` with the repositories; in the `CalculateDetoxRewardsUseCase(...)` factory add `recordDailySaved = { date, ms -> get<DailySavedRepository>().record(date, ms) },`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon`
Expected: PASS (whole shared suite).

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "Progress: record each finished day's saved time on rollover"
```

---

### Task 3: `ProgressStats` + `GetProgressStatsUseCase` + quest count

**Files:** Modify `QuestDao.kt` + 3 fakes, `SharedModule.kt`; create `ProgressStats.kt`, `GetProgressStatsUseCase.kt`, `GetProgressStatsUseCaseTest.kt`, `QuestDaoCountTest.kt`.

**Interfaces:**
- Consumes: `DailySavedRepository` (Task 2), `CurrencyRepository.observePlayerStats()`, `BlocklistRepository.observeBlockedApps()`.
- Produces: `QuestDao.observeLifetimeCompletedCount(): Flow<Int>`; `data class DaySaved(val date: LocalDate, val savedMs: Long, val isToday: Boolean)`; `data class ProgressStats(streakDays: Int, reclaimedAllTimeMs: Long, bestDayMs: Long, appsGuarded: Int, questsCleared: Int, level: Int, xp: Long, xpToNextLevel: Long, last7Days: List<DaySaved>)` (package `com.questlog.domain.model`); `class GetProgressStatsUseCase(currencyRepo, dailySavedRepo, questDao: QuestDao, blocklistRepo, clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault()) { operator fun invoke(): Flow<ProgressStats> }`.

- [ ] **Step 1: Count query + fakes** (compile prerequisite)

`QuestDao.kt`:

```kotlin
    @Query("SELECT COUNT(*) FROM quest_completions")
    fun observeLifetimeCompletedCount(): Flow<Int>
```

Add to each fake (`DailyQuestRepositoryTest.FakeQuestDao`, `EvaluateDailyQuestsUseCaseTest.FakeQuestDao`, `DashboardViewModelTest.FakeQuestDao`):

```kotlin
    override fun observeLifetimeCompletedCount(): Flow<Int> = MutableStateFlow(0)
```

(import `kotlinx.coroutines.flow.MutableStateFlow` where missing).

- [ ] **Step 2: Write the failing tests**

`shared/src/desktopTest/kotlin/com/questlog/data/local/QuestDaoCountTest.kt`:

```kotlin
package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.QuestCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestDaoCountTest {
    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    private val dao = db.questDao()

    @AfterTest fun tearDown() = db.close()

    @Test
    fun `lifetime count spans every day`() = runTest {
        assertEquals(0, dao.observeLifetimeCompletedCount().first())
        dao.insertIfAbsent(QuestCompletion("2026-09-20", "a", 0))
        dao.insertIfAbsent(QuestCompletion("2026-09-21", "a", 0))
        dao.insertIfAbsent(QuestCompletion("2026-09-21", "b", 0))
        assertEquals(3, dao.observeLifetimeCompletedCount().first())
    }
}
```

`shared/src/commonTest/kotlin/com/questlog/domain/usecase/GetProgressStatsUseCaseTest.kt`:

```kotlin
package com.questlog.domain.usecase

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.local.entity.DailySaved
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.model.ProgressStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ProgressClock(private val at: Instant) : Clock { override fun now() = at }

private class HistoryDao(rows: List<DailySaved>) : DailySavedDao {
    private val all = rows
    override suspend fun upsert(row: DailySaved) = Unit
    override fun observeSince(fromDate: String): Flow<List<DailySaved>> =
        MutableStateFlow(all.filter { it.date >= fromDate }.sortedBy { it.date })
    override fun observeBestMs(): Flow<Long?> = MutableStateFlow(all.maxOfOrNull { it.savedMs })
}

private class CountQuestDao(private val count: Int) : QuestDao {
    override suspend fun insertIfAbsent(completion: QuestCompletion) = 1L
    override fun observeCompletedIds(date: String): Flow<List<String>> = MutableStateFlow(emptyList())
    override suspend fun completedIds(date: String) = emptyList<String>()
    override fun observeLifetimeCompletedCount(): Flow<Int> = MutableStateFlow(count)
}

private class TwoBlocked : BlocklistDao {
    private val rows = listOf(BlockedAppEntity("a", 0), BlockedAppEntity("b", 0))
    override fun observeAll(): Flow<List<BlockedAppEntity>> = MutableStateFlow(rows)
    override suspend fun getAll() = rows
    override suspend fun get(packageName: String) = rows.find { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) = Unit
    override suspend fun delete(packageName: String) = Unit
}

class GetProgressStatsUseCaseTest {
    // "today" is 2026-09-27 in UTC
    private val clock = ProgressClock(Instant.parse("2026-09-27T12:00:00Z"))
    private val today = LocalDate(2026, 9, 27)

    private suspend fun stats(history: List<DailySaved>, todayMs: Long, streak: Int = 4): ProgressStats {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(awardedSavedMsToday = todayMs, lifetimeSavedMs = 3_600_000L, consecutiveDetoxDays = streak, xp = 150)
            flow.value = balance
        }
        return GetProgressStatsUseCase(
            currencyRepo = CurrencyRepository(currencyDao),
            dailySavedRepo = DailySavedRepository(HistoryDao(history)),
            questDao = CountQuestDao(7),
            blocklistRepo = BlocklistRepository(TwoBlocked()),
            clock = clock,
            timeZone = TimeZone.UTC,
        )().first()
    }

    @Test
    fun `seven contiguous days ending today, missing days are zero`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-25", 30 * 60_000L)), todayMs = 5 * 60_000L)
        assertEquals(7, s.last7Days.size)
        assertEquals((6 downTo 0).map { LocalDate(2026, 9, 27 - it) }, s.last7Days.map { it.date })
        assertTrue(s.last7Days.last().isToday)
        assertEquals(listOf(0L, 0L, 0L, 0L, 30 * 60_000L, 0L, 5 * 60_000L), s.last7Days.map { it.savedMs })
    }

    @Test
    fun `today uses the live value even if a row exists for today`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-27", 99 * 60_000L)), todayMs = 5 * 60_000L)
        assertEquals(5 * 60_000L, s.last7Days.last().savedMs)
    }

    @Test
    fun `best day includes today, and an empty table falls back to today`() = runTest {
        assertEquals(20 * 60_000L, stats(listOf(DailySaved("2026-09-01", 10 * 60_000L)), todayMs = 20 * 60_000L).bestDayMs)
        assertEquals(50 * 60_000L, stats(listOf(DailySaved("2026-09-01", 50 * 60_000L)), todayMs = 20 * 60_000L).bestDayMs)
        assertEquals(20 * 60_000L, stats(emptyList(), todayMs = 20 * 60_000L).bestDayMs)
    }

    @Test
    fun `a malformed date row is ignored`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-26", 10 * 60_000L), DailySaved("2026-9-26x", 99 * 60_000L)), todayMs = 0L)
        assertEquals(7, s.last7Days.size)
        assertEquals(10 * 60_000L, s.last7Days[5].savedMs)
    }

    @Test
    fun `lifetime numbers pass through`() = runTest {
        val s = stats(emptyList(), todayMs = 60_000L, streak = 4)
        assertEquals(4, s.streakDays)
        assertEquals(3_600_000L + 60_000L, s.reclaimedAllTimeMs)
        assertEquals(2, s.appsGuarded)
        assertEquals(7, s.questsCleared)
        assertEquals(150L, s.xp)
        assertEquals(today, s.last7Days.last().date)
    }
}
```

(`FakeCurrencyDao` is the public fake in the same package from `CalculateDetoxRewardsUseCaseTest`; its `flow` must be updated after changing `balance`, as above.)

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.domain.usecase.GetProgressStatsUseCaseTest" --tests "com.questlog.data.local.QuestDaoCountTest"`
Expected: FAIL — `GetProgressStatsUseCase` / `ProgressStats` unresolved.

- [ ] **Step 4: Implement**

`shared/src/commonMain/kotlin/com/questlog/domain/model/ProgressStats.kt`:

```kotlin
package com.questlog.domain.model

import kotlinx.datetime.LocalDate

data class DaySaved(val date: LocalDate, val savedMs: Long, val isToday: Boolean)

/** The Progress screen's long view. [last7Days] is exactly 7, chronological, `[6]` is today. */
data class ProgressStats(
    val streakDays: Int,
    val reclaimedAllTimeMs: Long,
    val bestDayMs: Long,
    val appsGuarded: Int,
    val questsCleared: Int,
    val level: Int,
    val xp: Long,
    val xpToNextLevel: Long,
    val last7Days: List<DaySaved>,
)
```

`shared/src/commonMain/kotlin/com/questlog/domain/usecase/GetProgressStatsUseCase.kt`:

```kotlin
package com.questlog.domain.usecase

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.model.DaySaved
import com.questlog.domain.model.ProgressStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
                // Today is live; a daily_saved row for today (should never exist) is ignored.
                DaySaved(d, if (isToday) stats.todaySavedMs else (byDate[d] ?: 0L), isToday)
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

`SharedModule.kt`: import `com.questlog.domain.usecase.GetProgressStatsUseCase`; add

```kotlin
    factory {
        GetProgressStatsUseCase(
            currencyRepo = get(), dailySavedRepo = get(), questDao = get(), blocklistRepo = get(),
        )
    }
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :shared:desktopTest :app:testDebugUnitTest --no-daemon`
Expected: PASS (both suites — the app suite proves the `DashboardViewModelTest` fake compiles).

Note on "best day with a today row": `observeBestMs` is table-wide, so a stray today-row would raise best day. Accepted: rows are only ever written for `lastDay` on rollover, so a today-row only appears if the device clock moves backwards.

- [ ] **Step 6: Commit**

```bash
git add shared/src app/src/test
git commit -m "Progress: ProgressStats, GetProgressStatsUseCase, lifetime quest count"
```

---

### Task 4: `ProgressViewModel` + pure format helpers

**Files:** Create `ui/progress/ProgressFormat.kt`, `ui/progress/ProgressViewModel.kt`, `ProgressFormatTest.kt`, `ProgressViewModelTest.kt`; modify `QuestLogApp.kt`.

**Interfaces:**
- Consumes: `GetProgressStatsUseCase`, `ProgressStats`, `DaySaved` (Task 3).
- Produces (package `com.example.questlog.ui.progress`): `fun dayInitial(day: DayOfWeek): String`; `fun showsEmptyWeek(days: List<DaySaved>): Boolean`; `fun barFraction(savedMs: Long, days: List<DaySaved>): Float`; `fun xpToGo(xp: Long, xpToNextLevel: Long): Long`; `fun reclaimedLine(ms: Long): String` (`"1h 30m"` / `"45m"`); `data class ProgressUiState(val isLoading: Boolean = true, val stats: ProgressStats? = null)`; `class ProgressViewModel(getProgressStats: GetProgressStatsUseCase) { val uiState: StateFlow<ProgressUiState> }`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/questlog/ui/progress/ProgressFormatTest.kt`:

```kotlin
package com.example.questlog.ui.progress

import com.questlog.domain.model.DaySaved
import com.questlog.util.TimeConversion
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressFormatTest {
    private fun week(vararg ms: Long) = ms.mapIndexed { i, v -> DaySaved(LocalDate(2026, 9, 21 + i), v, i == ms.lastIndex) }

    @Test fun `day initials`() = assertEquals(
        listOf("M", "T", "W", "T", "F", "S", "S"),
        DayOfWeek.entries.map { dayInitial(it) },
    )

    @Test fun `empty week until two days have saved time - zero days don't count`() {
        assertTrue(showsEmptyWeek(week(0, 0, 0, 0, 0, 0, 0)))
        assertTrue(showsEmptyWeek(week(0, 0, 0, 0, 0, 0, 5)))
        assertFalse(showsEmptyWeek(week(0, 0, 0, 0, 0, 3, 5)))
    }

    @Test fun `bars scale to the week's max, and an all-zero week never divides by zero`() {
        val w = week(0, 0, 0, 0, 0, 10, 40)
        assertEquals(1f, barFraction(40, w))
        assertEquals(0.25f, barFraction(10, w))
        assertEquals(0f, barFraction(0, week(0, 0, 0, 0, 0, 0, 0)))
    }

    @Test fun `xp to go stays positive exactly on a level boundary`() {
        val atLevel3 = TimeConversion.xpForLevel(3)
        assertEquals(TimeConversion.xpForLevel(4) - atLevel3, xpToGo(atLevel3, TimeConversion.xpForLevel(4)))
        assertTrue(xpToGo(atLevel3, TimeConversion.xpForLevel(4)) > 0)
    }

    @Test fun `reclaimed line`() {
        assertEquals("1h 30m", reclaimedLine(90 * 60_000L))
        assertEquals("45m", reclaimedLine(45 * 60_000L))
        assertEquals("0m", reclaimedLine(0))
    }
}
```

`app/src/test/java/com/example/questlog/ui/progress/ProgressViewModelTest.kt`:

```kotlin
package com.example.questlog.ui.progress

import com.example.questlog.ui.dashboard.FakeCurrencyDao
import com.example.questlog.ui.dashboard.FakeQuestDao
import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.entity.DailySaved
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.usecase.GetProgressStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NoHistory : DailySavedDao {
    override suspend fun upsert(row: DailySaved) = Unit
    override fun observeSince(fromDate: String): Flow<List<DailySaved>> = MutableStateFlow(emptyList())
    override fun observeBestMs(): Flow<Long?> = MutableStateFlow(null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the use case's stats`() = runTest {
        val vm = ProgressViewModel(
            GetProgressStatsUseCase(
                currencyRepo = CurrencyRepository(FakeCurrencyDao()),
                dailySavedRepo = DailySavedRepository(NoHistory()),
                questDao = FakeQuestDao(),
                blocklistRepo = BlocklistRepository(com.example.questlog.ui.dashboard.FakeBlocklistDao()),
            ),
        )
        assertTrue(vm.uiState.value.isLoading)
        backgroundScope.launch { vm.uiState.collect {} } // WhileSubscribed needs a subscriber
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(false, s.isLoading)
        assertEquals(7, s.stats?.last7Days?.size)
        assertEquals(3, s.stats?.streakDays) // DashboardViewModelTest's FakeCurrencyDao starts at a 3-day streak
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.progress.*"`
Expected: FAIL — `dayInitial`, `ProgressViewModel` etc. unresolved.

- [ ] **Step 3: Implement**

`app/src/main/java/com/example/questlog/ui/progress/ProgressFormat.kt`:

```kotlin
package com.example.questlog.ui.progress

import com.example.questlog.ui.format.formatReclaimed
import com.questlog.domain.model.DaySaved
import kotlinx.datetime.DayOfWeek

fun dayInitial(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
    else -> ""
}

/** Show the empty state until at least two of the seven days have any saved time. */
fun showsEmptyWeek(days: List<DaySaved>): Boolean = days.count { it.savedMs > 0 } < 2

/** A bar's height as a fraction of the week's best day (0 for an all-zero week). */
fun barFraction(savedMs: Long, days: List<DaySaved>): Float =
    savedMs.toFloat() / days.maxOf { it.savedMs }.coerceAtLeast(1L)

/** XP still needed for the next level; [xpToNextLevel] is that level's total threshold. */
fun xpToGo(xp: Long, xpToNextLevel: Long): Long = (xpToNextLevel - xp).coerceAtLeast(0L)

/** "1h 30m" / "45m". */
fun reclaimedLine(ms: Long): String = formatReclaimed(ms).let { "${it.hours ?: ""} ${it.minutes}".trim() }
```

(`else` covers kotlinx-datetime's `expect enum` exhaustiveness on some targets; remove it if the compiler flags it as redundant.)

`app/src/main/java/com/example/questlog/ui/progress/ProgressViewModel.kt`:

```kotlin
package com.example.questlog.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questlog.domain.model.ProgressStats
import com.questlog.domain.usecase.GetProgressStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(val isLoading: Boolean = true, val stats: ProgressStats? = null)

class ProgressViewModel(getProgressStats: GetProgressStatsUseCase) : ViewModel() {
    val uiState: StateFlow<ProgressUiState> =
        getProgressStats()
            .map { ProgressUiState(isLoading = false, stats = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
```

`QuestLogApp.kt`: import `com.example.questlog.ui.progress.ProgressViewModel`; in `appModule` add `viewModel { ProgressViewModel(get()) }`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.progress.*"`
Expected: PASS (5 format + 1 VM).

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Progress: ProgressViewModel and chart/format helpers"
```

---

### Task 5: Progress screen, ring entry point, navigation

**Files:** Create `ui/progress/ProgressScreen.kt`; modify `TodayHero.kt`, `TodayScreen.kt`, `QuestLogRoot.kt`, `app/src/androidTest/.../TodayScreenTest.kt`.

**Interfaces:**
- Consumes: Task 4 helpers and `ProgressUiState`/`ProgressViewModel`.
- Produces: `@Composable fun ProgressScreen(state: ProgressUiState, onBack: () -> Unit)`; `TodayHero(stats, onOpenProgress: () -> Unit, modifier)`; `TodayScreen(..., onOpenUnlock, onOpenProgress: () -> Unit)`.

UI wiring; the gate is compilation plus the suites (behaviour is covered by Tasks 3–4).

- [ ] **Step 1: `ProgressScreen.kt`**

```kotlin
package com.example.questlog.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold
import com.example.questlog.ui.format.levelTitle
import com.questlog.domain.model.DaySaved
import com.questlog.domain.model.ProgressStats
import com.questlog.util.TimeConversion
import kotlinx.datetime.LocalDate

@Composable
fun ProgressScreen(state: ProgressUiState, onBack: () -> Unit) {
    val c = QuestLogTheme.colors
    QuestScaffold(
        header = {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary) }
                Text("Progress", style = QuestType.screenTitle, color = c.inkPrimary)
            }
        },
    ) {
        Hairline()
        val s = state.stats ?: return@QuestScaffold
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(top = QuestSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.lg),
        ) {
            Column {
                Text("Reclaimed all-time".uppercase(), style = QuestType.label, color = c.inkMuted)
                Text(reclaimedLine(s.reclaimedAllTimeMs), style = QuestType.display, color = c.inkPrimary)
            }
            Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
                    StatTile("Streak", "${s.streakDays}d", Modifier.weight(1f), valueColor = c.earned)
                    StatTile("Best day", reclaimedLine(s.bestDayMs), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
                    StatTile("Apps guarded", "${s.appsGuarded}", Modifier.weight(1f))
                    StatTile("Quests cleared", "${s.questsCleared}", Modifier.weight(1f))
                }
            }
            Hairline()
            LevelBlock(s)
            Hairline()
            ReclaimedChart(s.last7Days)
            Spacer(Modifier.height(QuestSpacing.xxl))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = QuestLogTheme.colors.inkPrimary) {
    val c = QuestLogTheme.colors
    Column(
        modifier
            .clip(QuestShapes.small)
            .border(1.dp, c.rule, QuestShapes.small)
            .heightIn(min = 72.dp)
            .padding(QuestSpacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label.uppercase(), style = QuestType.caption, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.sm))
        Text(value, style = QuestType.display.copy(fontSize = 26.sp, lineHeight = 28.sp), color = valueColor)
    }
}

@Composable
private fun LevelBlock(s: ProgressStats) {
    val c = QuestLogTheme.colors
    val progress = TimeConversion.xpProgress(s.xp)
    Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${s.level} · ${levelTitle(s.level)}", style = QuestType.bodySmall, color = c.inkMuted)
            Text("${(progress * 100).toInt()}%", style = QuestType.serifNumeral, color = c.inkMuted)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.rule)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.inkSecondary))
        }
        Text(
            "${xpToGo(s.xp, s.xpToNextLevel)} XP to level ${s.level + 1}".uppercase(),
            style = QuestType.caption,
            color = c.inkMuted,
        )
    }
}

@Composable
private fun ReclaimedChart(days: List<DaySaved>) {
    val c = QuestLogTheme.colors
    Column {
        Text("Last 7 days".uppercase(), style = QuestType.label, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.md))
        if (showsEmptyWeek(days)) {
            Text("Your week fills in here as you reclaim time.", style = QuestType.bodySmall, color = c.inkMuted)
            return
        }
        Row(
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Last 7 days: " + days.joinToString { "${it.date.dayOfWeek.name.lowercase()} ${reclaimedLine(it.savedMs)}" }
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((72.dp * barFraction(d.savedMs, days)).coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (d.isToday) c.earned else c.inkSecondary),
                    )
                    Spacer(Modifier.height(QuestSpacing.xs))
                    Text(dayInitial(d.date.dayOfWeek), style = QuestType.caption, color = c.inkMuted)
                }
            }
        }
    }
}

private fun previewStats(week: List<Long>) = ProgressStats(
    streakDays = 6, reclaimedAllTimeMs = 14 * 3_600_000L + 20 * 60_000L, bestDayMs = 88 * 60_000L,
    appsGuarded = 7, questsCleared = 23, level = 3, xp = 420, xpToNextLevel = 600,
    last7Days = week.mapIndexed { i, ms -> DaySaved(LocalDate(2026, 9, 21 + i), ms, i == 6) },
)

@Preview(name = "Progress")
@Composable
private fun ProgressScreenPreview() {
    QuestLogTheme {
        ProgressScreen(ProgressUiState(false, previewStats(listOf(40, 62, 0, 88, 51, 30, 22).map { it * 60_000L })), onBack = {})
    }
}

@Preview(name = "Progress — empty week")
@Composable
private fun ProgressScreenPreview_EmptyWeek() {
    QuestLogTheme {
        ProgressScreen(ProgressUiState(false, previewStats(listOf(0, 0, 0, 0, 0, 0, 12).map { it * 60_000L })), onBack = {})
    }
}
```

(If `72.dp * Float` doesn't resolve, use `(72 * barFraction(...)).dp`.)

- [ ] **Step 2: Ring entry point**

`TodayHero.kt`: signature `fun TodayHero(stats: PlayerStats, onOpenProgress: () -> Unit, modifier: Modifier = Modifier)`; import `androidx.compose.foundation.clickable`; the ring modifier becomes

```kotlin
                modifier = Modifier
                    .clickable(onClickLabel = "Open progress", onClick = onOpenProgress)
                    .clearAndSetSemantics { contentDescription = ringDescription },
```

and the preview call becomes `TodayHero(fakeStats(), onOpenProgress = {})`.

`TodayScreen.kt`: add `onOpenProgress: () -> Unit,` after `onOpenUnlock`; `TodayHero(state.stats, onOpenProgress)`; previews add `onOpenProgress = {}`.

`app/src/androidTest/java/com/example/questlog/ui/TodayScreenTest.kt`: each positional `TodayScreen(state…, {}, {}, …)` call gets one more trailing `{}` argument.

- [ ] **Step 3: Navigation (`QuestLogRoot.kt`)**

- `private enum class Screen { Today, Realm, Blocklist, Unlock, Progress }`
- imports `com.example.questlog.ui.progress.ProgressScreen`, `com.example.questlog.ui.progress.ProgressViewModel`
- in the `TodayScreen(...)` call: `onOpenProgress = { screen = Screen.Progress },`
- new branch after `Screen.Unlock -> { … }`:

```kotlin
                    Screen.Progress -> {
                        val progressVm = koinViewModel<ProgressViewModel>()
                        val progressState by progressVm.uiState.collectAsState()
                        ProgressScreen(state = progressState, onBack = { screen = Screen.Today })
                    }
```

- [ ] **Step 4: Compile and run**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Progress: screen, ring entry point, navigation"
```

---

### Task 6: Docs + full check

**Files:** Modify `README.md`, `CLAUDE.md`.

- [ ] **Step 1: README** — screens paragraph: add **Progress** ("tap the streak ring on Today": all-time reclaimed, streak / best day / apps guarded / quests cleared, level track, 7-day chart). *Persistence*: schema **v11**, migrations `1→…→11`; add row `| \`daily_saved\` | \`date\` | \`savedMs\` — each finished day's reclaimed time, written on rollover (no backfill) |`.

- [ ] **Step 2: CLAUDE.md** — in *Testing patterns*, the fake-DAO bullet: add `` Adding a `QuestDao` method means updating `FakeQuestDao` in `DailyQuestRepositoryTest`, `EvaluateDailyQuestsUseCaseTest` and `DashboardViewModelTest`. `` In *Invariants / gotchas* add:

```markdown
- `daily_saved` gets one row per *finished* day, written in `CalculateDetoxRewardsUseCase`'s
  rollover branch (even a 0 day); today is never stored — `GetProgressStatsUseCase` uses the
  live `PlayerStats.todaySavedMs` for today's bar and "best day".
```

- [ ] **Step 3: Full check**

Run: `./gradlew :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "Docs: Progress screen and daily_saved"
```

---

## Manual verification (not CI)

Emulator: tap the streak ring on Today → Progress opens; Back returns to Today; a fresh install shows the "Your week fills in here…" empty state; TalkBack on the ring announces the reclaimed description plus "Open progress".
