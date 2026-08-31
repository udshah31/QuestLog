# Blocklist Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user choose which apps count as distractions — with an optional per-app daily allowance — from an in-app screen, replacing the hardcoded `defaultFlaggedPackages` set.

**Architecture:** A new Room table `blocked_app` in `shared` (`commonMain`) holds the list; a `BlocklistRepository` exposes it as a `Flow` and a suspend read. The two detox use cases take a `suspend () -> List<BlockedApp>` supplier instead of a fixed `Set<String>`, so every monitor tick reads the current list. Per-app allowances are applied as overage (`max(0, usage − allowance)`) inside `ScreenTimeRepository` before the existing `DetoxBudget.savedTimeMs` math. The `app` side adds an `InstalledAppsProvider` (PackageManager), a `BlocklistViewModel`, and a `BlocklistScreen` reached from a gear icon in the Today header as a third screen in `QuestLogRoot`.

**Tech Stack:** Kotlin 2.3.21, KMP (`shared` commonMain/androidMain/desktop), Room 2.8.4 (KMP, bundled SQLite driver, `@ConstructedBy`), Koin 4.1.0, Jetpack Compose (BOM 2026.03.01) Material 3, JUnit4 + `kotlin.test` + `kotlinx-coroutines-test`. minSdk 26 / compileSdk 36 / JDK 21. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-29-blocklist-editor-design.md` — read it alongside this plan.

## Global Constraints

- `shared` package root is `com.questlog`; `app` is `com.example.questlog` (`R` is `com.example.questlog.R`). Do not change `namespace`, `applicationId`, or `versionName`.
- minSdk 26, compileSdk 36, JDK 21, Kotlin 2.3.21, Room 2.8.4, Compose BOM 2026.03.01, Koin 4.1.0. No new libraries — `androidx.core:core-ktx` (`drawable.toBitmap()`) and `androidx.lifecycle:lifecycle-runtime-compose` (`LifecycleResumeEffect`) are already app dependencies.
- `DashboardViewModel`'s constructor, name, and `appModule` wiring do **not** change.
- Room migrations live in `shared/src/commonMain/.../data/local/QuestLogMigrations.kt`; `DatabaseFactory` (androidMain) wires `*questLogMigrations`. A migration's SQL is self-contained — never references a Kotlin constant that could later change.
- Room exports schema JSON on build to `shared/schemas/com.questlog.data.local.QuestLogDatabase/N.json` — commit the new `8.json`.
- CI (`ci.yml`) runs only `:shared:desktopTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`. Tests under `shared/src/androidUnitTest` and `app/src/androidTest` do NOT run in CI. Put shared logic tests in `commonTest` (runs on desktop) or `desktopTest`.
- `app` UI colour comes from `QuestLogTheme.colors` or `MaterialTheme.colorScheme`, never a raw `Color(...)`. Spacing from `QuestSpacing`, type from `QuestType`, shapes from `QuestShapes`. No emoji in persistent UI.
- Every new public composable gets `@Preview` for both light and default (`@Preview(uiMode = UI_MODE_NIGHT_YES)` and plain), using hand-built fakes — never real repositories.
- Use `--no-daemon` for Gradle; add `--rerun-tasks` to force test re-run.
- Commit at the end of every task. Commit-message trailer:
  ```

  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_016x12yvauoZpGqRbF3jGPxJ
  ```

---

## File Structure

```
shared/src/commonMain/kotlin/com/questlog/
  domain/model/BlockedApp.kt                       CREATE  — data class BlockedApp
  domain/model/DefaultBlocklist.kt                 CREATE  — defaultFlaggedPackages (moved from di/)
  data/local/entity/BlockedAppEntity.kt            CREATE  — @Entity("blocked_app")
  data/local/dao/BlocklistDao.kt                   CREATE  — CRUD + observe
  data/local/QuestLogDatabase.kt                   MODIFY  — entity, version 8, blocklistDao()
  data/local/QuestLogMigrations.kt                 MODIFY  — MIGRATION_7_8 + questLogSeedCallback
  data/repository/BlocklistRepository.kt           CREATE  — Flow + suspend read + writes
  data/repository/ScreenTimeRepository.kt          MODIFY  — allowances overage in fetchAndPersistToday
  util/DetoxBudget.kt                              MODIFY  — chargeableMs(usageMs, allowanceMs)
  domain/usecase/CalculateDetoxRewardsUseCase.kt   MODIFY  — blockedApps supplier
  domain/usecase/EvaluateDailyQuestsUseCase.kt     MODIFY  — blockedApps supplier
  di/SharedModule.kt                               MODIFY  — BlocklistRepository single; suppliers; import move
shared/src/androidMain/kotlin/com/questlog/
  data/local/DatabaseFactory.kt                    MODIFY  — .addCallback(questLogSeedCallback)
  di/PlatformModule.android.kt                     MODIFY  — blocklistDao() single
shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json   CREATE (generated, commit)

shared/src/commonTest/kotlin/com/questlog/
  util/DetoxBudgetTest.kt                          MODIFY/CREATE — chargeableMs cases
  data/repository/BlocklistRepositoryTest.kt       CREATE  — fake DAO
  domain/usecase/CalculateDetoxRewardsUseCaseTest.kt  MODIFY — supplier param + allowance case
  domain/usecase/EvaluateDailyQuestsUseCaseTest.kt    MODIFY — supplier param
shared/src/desktopTest/kotlin/com/questlog/data/local/
  ScreenTimeMigrationTest.kt                       MODIFY  — `7 to 8` test
  BlocklistDaoTest.kt                              CREATE  — real in-memory Room + seed callback

app/src/main/java/com/example/questlog/
  data/InstalledAppsProvider.kt                    CREATE  — interface + PackageManager impl
  theme/QuestIcons.kt                              MODIFY  — Settings (gear) glyph
  ui/blocklist/BlocklistViewModel.kt               CREATE  — state + intents
  ui/blocklist/BlocklistRow.kt                     CREATE  — one app row + limit chips
  ui/blocklist/BlocklistScreen.kt                  CREATE  — scaffold + permission banner + list
  ui/today/TodayScreen.kt                          MODIFY  — gear icon + onOpenBlocklist param
  ui/QuestLogRoot.kt                               MODIFY  — Screen.Blocklist
  QuestLogApp.kt                                   MODIFY  — Koin: provider + viewModel
app/src/test/java/com/example/questlog/
  ui/blocklist/BlocklistViewModelTest.kt           CREATE  — JVM, fakes
app/src/androidTest/java/com/example/questlog/
  ui/BlocklistScreenTest.kt                        CREATE  — instrumented, NOT in CI

README.md, CHANGELOG.md, CLAUDE.md                 MODIFY  (final task)
```

---

## Task 1: `DetoxBudget.chargeableMs` (pure, TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/questlog/util/DetoxBudget.kt`
- Test: `shared/src/commonTest/kotlin/com/questlog/util/DetoxBudgetTest.kt` (create if absent)

**Interfaces:**
- Produces: `fun DetoxBudget.chargeableMs(usageMs: Long, allowanceMs: Long): Long`

- [ ] **Step 1: Write the failing test**

Create/append `shared/src/commonTest/kotlin/com/questlog/util/DetoxBudgetTest.kt`:

```kotlin
package com.questlog.util

import kotlin.test.Test
import kotlin.test.assertEquals

class DetoxBudgetTest {

    @Test
    fun `chargeableMs is zero when usage is within the allowance`() {
        assertEquals(0L, DetoxBudget.chargeableMs(usageMs = 10 * 60_000L, allowanceMs = 30 * 60_000L))
        assertEquals(0L, DetoxBudget.chargeableMs(usageMs = 30 * 60_000L, allowanceMs = 30 * 60_000L))
    }

    @Test
    fun `chargeableMs is the overage when usage exceeds the allowance`() {
        assertEquals(5 * 60_000L, DetoxBudget.chargeableMs(usageMs = 35 * 60_000L, allowanceMs = 30 * 60_000L))
    }

    @Test
    fun `chargeableMs with a zero allowance charges all usage`() {
        assertEquals(42 * 60_000L, DetoxBudget.chargeableMs(usageMs = 42 * 60_000L, allowanceMs = 0L))
    }

    @Test
    fun `chargeableMs never goes negative`() {
        assertEquals(0L, DetoxBudget.chargeableMs(usageMs = 0L, allowanceMs = 30 * 60_000L))
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.util.DetoxBudgetTest'`
Expected: FAIL — `Unresolved reference: chargeableMs`.

- [ ] **Step 3: Write the implementation**

In `DetoxBudget.kt`, add inside the `object DetoxBudget`:

```kotlin
    /**
     * The part of [usageMs] that counts against the player: everything beyond
     * [allowanceMs]. An allowance of 0 charges the full usage (fully-blocked app).
     */
    fun chargeableMs(usageMs: Long, allowanceMs: Long): Long =
        (usageMs - allowanceMs).coerceAtLeast(0L)
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.util.DetoxBudgetTest'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/util/DetoxBudget.kt shared/src/commonTest/kotlin/com/questlog/util/DetoxBudgetTest.kt
git commit -m "Add DetoxBudget.chargeableMs overage helper"
```

---

## Task 2: `BlockedApp` model + move `defaultFlaggedPackages`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/questlog/domain/model/BlockedApp.kt`
- Create: `shared/src/commonMain/kotlin/com/questlog/domain/model/DefaultBlocklist.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt` (remove the `val`, add an import)

**Interfaces:**
- Produces:
  - `data class BlockedApp(val packageName: String, val dailyLimitMs: Long)`
  - `val defaultFlaggedPackages: Set<String>` in package `com.questlog.domain.model`

- [ ] **Step 1: Create `domain/model/BlockedApp.kt`**

```kotlin
package com.questlog.domain.model

/**
 * An app the user has marked as a distraction. [dailyLimitMs] is a daily
 * allowance — only foreground time beyond it counts against the detox reward.
 * A limit of 0 means the app is fully blocked (every millisecond counts).
 */
data class BlockedApp(
    val packageName: String,
    val dailyLimitMs: Long,
)
```

- [ ] **Step 2: Create `domain/model/DefaultBlocklist.kt`**

```kotlin
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
```

- [ ] **Step 3: Update `di/SharedModule.kt`**

Delete the `val defaultFlaggedPackages = setOf(...)` block and its doc comment (lines 15–26). Add this import next to the other `com.questlog.*` imports:

```kotlin
import com.questlog.domain.model.defaultFlaggedPackages
```

Leave the two `flaggedPackages = defaultFlaggedPackages` references in the factories as-is for now — Task 6 replaces them.

- [ ] **Step 4: Build**

Run: `./gradlew :shared:assemble --no-daemon`
Expected: `BUILD SUCCESSFUL` — the moved constant resolves via the new import.

- [ ] **Step 5: Run shared tests — nothing regressed**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/domain/model/BlockedApp.kt shared/src/commonMain/kotlin/com/questlog/domain/model/DefaultBlocklist.kt shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt
git commit -m "Add BlockedApp model; move defaultFlaggedPackages to domain/model"
```

---

## Task 3: `blocked_app` table — entity, DAO, migration, seed callback, schema

**Files:**
- Create: `shared/src/commonMain/kotlin/com/questlog/data/local/entity/BlockedAppEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/questlog/data/local/dao/BlocklistDao.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogDatabase.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogMigrations.kt`
- Modify: `shared/src/androidMain/kotlin/com/questlog/data/local/DatabaseFactory.kt`
- Create: `shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json` (generated)
- Test: `shared/src/desktopTest/kotlin/com/questlog/data/local/ScreenTimeMigrationTest.kt` (add one test)

**Interfaces:**
- Consumes: `com.questlog.domain.model.defaultFlaggedPackages` (Task 2)
- Produces:
  - `@Entity(tableName = "blocked_app") data class BlockedAppEntity(val packageName: String, val dailyLimitMs: Long)`
  - `interface BlocklistDao` with:
    - `fun observeAll(): Flow<List<BlockedAppEntity>>`
    - `suspend fun getAll(): List<BlockedAppEntity>`
    - `suspend fun get(packageName: String): BlockedAppEntity?`
    - `suspend fun upsert(app: BlockedAppEntity)`
    - `suspend fun delete(packageName: String)`
  - `QuestLogDatabase.blocklistDao(): BlocklistDao`, DB `version = 8`
  - `val MIGRATION_7_8: Migration`
  - `val questLogSeedCallback: RoomDatabase.Callback`

- [ ] **Step 1: Create `data/local/entity/BlockedAppEntity.kt`**

```kotlin
package com.questlog.data.local.entity

import androidx.room.Entity

/**
 * One row per app the user has marked as a distraction. Row existence *is* the
 * toggle — there is no `enabled` column. [dailyLimitMs] 0 = fully blocked.
 */
@Entity(tableName = "blocked_app", primaryKeys = ["packageName"])
data class BlockedAppEntity(
    val packageName: String,
    val dailyLimitMs: Long = 0L,
)
```

- [ ] **Step 2: Create `data/local/dao/BlocklistDao.kt`**

```kotlin
package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocked_app ORDER BY packageName")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_app ORDER BY packageName")
    suspend fun getAll(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_app WHERE packageName = :packageName")
    suspend fun get(packageName: String): BlockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_app WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
```

- [ ] **Step 3: Wire the entity + DAO into `QuestLogDatabase.kt`**

Add the import:

```kotlin
import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
```

Add `BlockedAppEntity::class` to the `entities = [ ... ]` array, change `version = 7` to `version = 8`, and add the accessor beside the others:

```kotlin
    abstract fun blocklistDao(): BlocklistDao
```

- [ ] **Step 4: Add `MIGRATION_7_8` and the seed callback to `QuestLogMigrations.kt`**

Add imports at the top:

```kotlin
import androidx.room.RoomDatabase
```

(`androidx.sqlite.SQLiteConnection` and `androidx.sqlite.execSQL` are already imported.)

Add before the `questLogMigrations` array:

```kotlin
/** v8: user-editable distraction list. Seeds the seven historical defaults. */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `blocked_app` (" +
                "`packageName` TEXT NOT NULL, `dailyLimitMs` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`packageName`))"
        )
        val seed = listOf(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.snapchat.android",
            "com.twitter.android", "com.reddit.frontpage", "com.google.android.youtube",
            "com.facebook.katana",
        )
        for (pkg in seed) {
            connection.execSQL(
                "INSERT OR IGNORE INTO `blocked_app` (`packageName`, `dailyLimitMs`) VALUES ('$pkg', 0)"
            )
        }
    }
}

/**
 * Seeds `blocked_app` on a fresh database (fresh installs never run migrations).
 * Uses the live [defaultFlaggedPackages] — unlike a migration, this always
 * represents "the current default", which is the right behaviour for a new user.
 */
val questLogSeedCallback = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        for (pkg in com.questlog.domain.model.defaultFlaggedPackages) {
            connection.execSQL(
                "INSERT OR IGNORE INTO `blocked_app` (`packageName`, `dailyLimitMs`) VALUES ('$pkg', 0)"
            )
        }
    }
}
```

Add `MIGRATION_7_8` to the `questLogMigrations` array:

```kotlin
internal val questLogMigrations: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
```

- [ ] **Step 5: Wire the callback into `DatabaseFactory.kt` (androidMain)**

Add the import:

```kotlin
import com.questlog.data.local.questLogSeedCallback
```

Add `.addCallback(questLogSeedCallback)` to the builder chain, right after `.addMigrations(*questLogMigrations)`:

```kotlin
            .addMigrations(*questLogMigrations)
            .addCallback(questLogSeedCallback)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
```

- [ ] **Step 6: Generate + inspect the schema**

Run: `./gradlew :shared:compileKotlinDesktop --no-daemon --rerun-tasks`
Expected: `BUILD SUCCESSFUL`; a new file `shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json` exists and contains the `blocked_app` table.

- [ ] **Step 7: Add the migration test**

In `shared/src/desktopTest/kotlin/com/questlog/data/local/ScreenTimeMigrationTest.kt`, add:

```kotlin
    @Test
    fun `7 to 8 creates blocked_app seeded with the seven defaults`() = runTest {
        helper.createDatabase(7).close()

        val v8 = helper.runMigrationsAndValidate(8, listOf(MIGRATION_7_8))

        val count = v8.queryLongs("SELECT COUNT(*) FROM blocked_app").single().single()
        assertEquals(7L, count)
        val limits = v8.queryLongs("SELECT DISTINCT dailyLimitMs FROM blocked_app")
        assertEquals(listOf(listOf(0L)), limits, "every seed row starts fully blocked")

        // packageName is the primary key.
        val rejectedDuplicate = runCatching {
            v8.execSQL("INSERT INTO blocked_app (packageName, dailyLimitMs) VALUES ('com.instagram.android', 5)")
        }.isFailure
        assertTrue(rejectedDuplicate)

        v8.close()
    }
```

- [ ] **Step 8: Run the migration tests**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.data.local.ScreenTimeMigrationTest'`
Expected: PASS — the existing `migrating 1 to 7 runs the full chain` still passes (it validates against `7.json`), and the new `7 to 8` test passes.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/data/local/ shared/src/androidMain/kotlin/com/questlog/data/local/DatabaseFactory.kt shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json shared/src/desktopTest/kotlin/com/questlog/data/local/ScreenTimeMigrationTest.kt
git commit -m "Add blocked_app table, v7 to v8 migration, and fresh-install seed callback"
```

---

## Task 4: `BlocklistRepository` + DI wiring + real-DB test

**Files:**
- Create: `shared/src/commonMain/kotlin/com/questlog/data/repository/BlocklistRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt` (add the `single`)
- Modify: `shared/src/androidMain/kotlin/com/questlog/di/PlatformModule.android.kt` (expose the DAO)
- Test: `shared/src/commonTest/kotlin/com/questlog/data/repository/BlocklistRepositoryTest.kt`
- Test: `shared/src/desktopTest/kotlin/com/questlog/data/local/BlocklistDaoTest.kt`

**Interfaces:**
- Consumes: `BlocklistDao` (Task 3), `BlockedApp` (Task 2)
- Produces: `open class BlocklistRepository(dao: BlocklistDao)` with
  - `fun observeBlockedApps(): Flow<List<BlockedApp>>`
  - `suspend fun current(): List<BlockedApp>`
  - `suspend fun setBlocked(packageName: String, blocked: Boolean)`
  - `suspend fun setLimit(packageName: String, dailyLimitMs: Long)`

- [ ] **Step 1: Write the failing repository test**

Create `shared/src/commonTest/kotlin/com/questlog/data/repository/BlocklistRepositoryTest.kt`:

```kotlin
package com.questlog.data.repository

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeBlocklistDao : BlocklistDao {
    val rows = mutableListOf<BlockedAppEntity>()
    private val flow = MutableStateFlow<List<BlockedAppEntity>>(emptyList())
    private fun emit() { flow.value = rows.sortedBy { it.packageName } }
    override fun observeAll(): Flow<List<BlockedAppEntity>> = flow
    override suspend fun getAll(): List<BlockedAppEntity> = rows.sortedBy { it.packageName }
    override suspend fun get(packageName: String): BlockedAppEntity? =
        rows.firstOrNull { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) {
        rows.removeAll { it.packageName == app.packageName }
        rows.add(app); emit()
    }
    override suspend fun delete(packageName: String) {
        rows.removeAll { it.packageName == packageName }; emit()
    }
}

class BlocklistRepositoryTest {

    @Test
    fun `setBlocked true inserts a fully-blocked row; false deletes it`() = runTest {
        val dao = FakeBlocklistDao()
        val repo = BlocklistRepository(dao)

        repo.setBlocked("com.instagram.android", blocked = true)
        assertEquals(0L, repo.current().single().dailyLimitMs)

        repo.setBlocked("com.instagram.android", blocked = false)
        assertTrue(repo.current().isEmpty())
    }

    @Test
    fun `setBlocked true keeps an existing limit`() = runTest {
        val dao = FakeBlocklistDao()
        val repo = BlocklistRepository(dao)
        repo.setLimit("com.reddit.frontpage", 30 * 60_000L)

        repo.setBlocked("com.reddit.frontpage", blocked = true)

        assertEquals(30 * 60_000L, repo.current().single().dailyLimitMs)
    }

    @Test
    fun `setLimit on an unblocked app blocks it with that limit`() = runTest {
        val repo = BlocklistRepository(FakeBlocklistDao())

        repo.setLimit("com.snapchat.android", 15 * 60_000L)

        val app = repo.current().single()
        assertEquals("com.snapchat.android", app.packageName)
        assertEquals(15 * 60_000L, app.dailyLimitMs)
    }

    @Test
    fun `observeBlockedApps emits the current list as domain models`() = runTest {
        val repo = BlocklistRepository(FakeBlocklistDao())
        repo.setBlocked("a", true)
        repo.setLimit("b", 1_000L)

        val list = repo.observeBlockedApps().first()

        assertEquals(listOf("a" to 0L, "b" to 1_000L), list.map { it.packageName to it.dailyLimitMs })
    }

    @Test
    fun `get returns null for an app that was never blocked`() = runTest {
        assertNull(FakeBlocklistDao().get("nope"))
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.data.repository.BlocklistRepositoryTest'`
Expected: FAIL — `Unresolved reference: BlocklistRepository`.

- [ ] **Step 3: Write `BlocklistRepository.kt`**

```kotlin
package com.questlog.data.repository

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.domain.model.BlockedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's distraction list. A row in `blocked_app` means the app is a
 * distraction; `dailyLimitMs` is its allowance (0 = fully blocked). `open` so
 * tests can stub it.
 */
open class BlocklistRepository(private val dao: BlocklistDao) {

    open fun observeBlockedApps(): Flow<List<BlockedApp>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    open suspend fun current(): List<BlockedApp> = dao.getAll().map { it.toModel() }

    /** Enable/disable an app as a distraction. Enabling keeps any existing limit. */
    open suspend fun setBlocked(packageName: String, blocked: Boolean) {
        if (blocked) {
            if (dao.get(packageName) == null) dao.upsert(BlockedAppEntity(packageName, 0L))
        } else {
            dao.delete(packageName)
        }
    }

    /** Set an app's daily allowance. A limit on an unblocked app blocks it. */
    open suspend fun setLimit(packageName: String, dailyLimitMs: Long) {
        dao.upsert(BlockedAppEntity(packageName, dailyLimitMs.coerceAtLeast(0L)))
    }

    private fun BlockedAppEntity.toModel() = BlockedApp(packageName, dailyLimitMs)
}
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.data.repository.BlocklistRepositoryTest'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Add the DI wiring**

In `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt`, add to the repositories block:

```kotlin
    single { BlocklistRepository(get()) }
```

and the import:

```kotlin
import com.questlog.data.repository.BlocklistRepository
```

In `shared/src/androidMain/kotlin/com/questlog/di/PlatformModule.android.kt`, add beside the other DAO singles:

```kotlin
    single { get<com.questlog.data.local.QuestLogDatabase>().blocklistDao() }
```

- [ ] **Step 6: Write the real-DB DAO test**

Create `shared/src/desktopTest/kotlin/com/questlog/data/local/BlocklistDaoTest.kt`:

```kotlin
package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlocklistDaoTest {

    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .addCallback(questLogSeedCallback)
            .build()

    private val dao = db.blocklistDao()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `fresh database is seeded with the seven defaults`() = runTest {
        assertEquals(7, dao.getAll().size)
    }

    @Test
    fun `upsert replaces the row for a package and delete removes it`() = runTest {
        dao.upsert(BlockedAppEntity("com.example.x", 10L))
        dao.upsert(BlockedAppEntity("com.example.x", 20L))
        assertEquals(20L, dao.get("com.example.x")?.dailyLimitMs)

        dao.delete("com.example.x")
        assertNull(dao.get("com.example.x"))
    }

    @Test
    fun `observeAll reflects writes`() = runTest {
        val before = dao.observeAll().first().size
        dao.upsert(BlockedAppEntity("com.example.y", 0L))
        assertEquals(before + 1, dao.observeAll().first().size)
    }
}
```

- [ ] **Step 7: Run the full shared test suite**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks`
Expected: all green, including the two new files.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/data/repository/BlocklistRepository.kt shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt shared/src/androidMain/kotlin/com/questlog/di/PlatformModule.android.kt shared/src/commonTest/kotlin/com/questlog/data/repository/BlocklistRepositoryTest.kt shared/src/desktopTest/kotlin/com/questlog/data/local/BlocklistDaoTest.kt
git commit -m "Add BlocklistRepository with Flow + suspend reads and DI wiring"
```

---

## Task 5: Apply per-app allowances in `ScreenTimeRepository`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/questlog/data/repository/ScreenTimeRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/questlog/data/repository/ScreenTimeRepositoryTest.kt` (create)

**Interfaces:**
- Consumes: `DetoxBudget.chargeableMs` (Task 1)
- Produces: `fetchAndPersistToday` gains a third parameter —
  `open suspend fun fetchAndPersistToday(flaggedPackages: Set<String>, startOfDayMs: Long, allowances: Map<String, Long> = emptyMap()): Long`

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/questlog/data/repository/ScreenTimeRepositoryTest.kt`:

```kotlin
package com.questlog.data.repository

import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.domain.model.AppUsage
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.util.DetoxBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class MapScreenTimeDao : ScreenTimeDao {
    val records = mutableListOf<ScreenTimeRecord>()
    override suspend fun upsert(record: ScreenTimeRecord) {
        records.removeAll { it.packageName == record.packageName && it.date == record.date }
        records.add(record)
    }
    override fun getByDate(date: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override suspend fun totalForegroundMsForDate(date: String): Long =
        records.filter { it.date == date }.sumOf { it.foregroundMs }
    override suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long =
        records.filter { it.date == date && it.packageName == packageName }.sumOf { it.foregroundMs }
}

/** Tracker that returns a fixed usage list regardless of the window. */
private class StubTracker(private val usage: List<AppUsage>) : ScreenTimeTracker() {
    override suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage> = usage
    override fun isPermissionGranted(): Boolean = true
}

class ScreenTimeRepositoryTest {

    private val budget = 90 * 60_000L

    @Test
    fun `with no allowances every flagged millisecond is charged`() = runTest {
        val repo = ScreenTimeRepository(
            MapScreenTimeDao(),
            StubTracker(listOf(AppUsage("com.insta", 20 * 60_000L))),
            dailyBudgetMs = budget,
        )
        // elapsed is large; saved = budget - 20min
        val saved = repo.fetchAndPersistToday(setOf("com.insta"), startOfDayMs = 0L)
        assertEquals(budget - 20 * 60_000L, saved)
    }

    @Test
    fun `usage within an app's allowance is not charged`() = runTest {
        val repo = ScreenTimeRepository(
            MapScreenTimeDao(),
            StubTracker(listOf(AppUsage("com.insta", 20 * 60_000L))),
            dailyBudgetMs = budget,
        )
        val saved = repo.fetchAndPersistToday(
            flaggedPackages = setOf("com.insta"),
            startOfDayMs = 0L,
            allowances = mapOf("com.insta" to 30 * 60_000L),
        )
        assertEquals(budget, saved, "20min < 30min allowance -> nothing charged")
    }

    @Test
    fun `only the overage beyond the allowance is charged`() = runTest {
        val repo = ScreenTimeRepository(
            MapScreenTimeDao(),
            StubTracker(listOf(AppUsage("com.insta", 50 * 60_000L))),
            dailyBudgetMs = budget,
        )
        val saved = repo.fetchAndPersistToday(
            flaggedPackages = setOf("com.insta"),
            startOfDayMs = 0L,
            allowances = mapOf("com.insta" to 30 * 60_000L),
        )
        assertEquals(budget - 20 * 60_000L, saved, "50min - 30min allowance = 20min charged")
    }

    @Test
    fun `raw foreground is still persisted regardless of the allowance`() = runTest {
        val dao = MapScreenTimeDao()
        val repo = ScreenTimeRepository(
            dao,
            StubTracker(listOf(AppUsage("com.insta", 50 * 60_000L))),
            dailyBudgetMs = budget,
        )
        repo.fetchAndPersistToday(setOf("com.insta"), 0L, mapOf("com.insta" to 30 * 60_000L))
        assertEquals(50 * 60_000L, dao.records.single().foregroundMs)
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.data.repository.ScreenTimeRepositoryTest'`
Expected: FAIL — `fetchAndPersistToday` has no `allowances` parameter.

- [ ] **Step 3: Update `ScreenTimeRepository.fetchAndPersistToday`**

Add the import:

```kotlin
import com.questlog.util.DetoxBudget
```

(It is likely already imported for `DetoxBudget.savedTimeMs` / `DEFAULT_DAILY_BUDGET_MS` — verify.)

Replace the method:

```kotlin
    open suspend fun fetchAndPersistToday(
        flaggedPackages: Set<String>,
        startOfDayMs: Long,
        allowances: Map<String, Long> = emptyMap(),
    ): Long {
        val endMs = Clock.System.now().toEpochMilliseconds()
        val flagged: List<AppUsage> = tracker.getUsageForPeriod(startOfDayMs, endMs)
            .filter { it.packageName in flaggedPackages }

        for (usage in flagged) {
            dao.upsert(ScreenTimeRecord(usage.packageName, today(), usage.totalForegroundMs))
        }

        val flaggedForegroundMs = flagged.sumOf { usage ->
            DetoxBudget.chargeableMs(usage.totalForegroundMs, allowances[usage.packageName] ?: 0L)
        }
        return DetoxBudget.savedTimeMs(
            budgetMs = dailyBudgetMs,
            elapsedMs = endMs - startOfDayMs,
            flaggedForegroundMs = flaggedForegroundMs,
        )
    }
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.data.repository.ScreenTimeRepositoryTest'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Run the full shared suite — the default arg keeps existing callers green**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks`
Expected: all green — `CalculateDetoxRewardsUseCase` still calls the 2-arg form (Task 6 changes that).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/data/repository/ScreenTimeRepository.kt shared/src/commonTest/kotlin/com/questlog/data/repository/ScreenTimeRepositoryTest.kt
git commit -m "ScreenTimeRepository: charge only per-app allowance overage"
```

---

## Task 6: Thread the blocklist supplier through the detox use cases

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCase.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/domain/usecase/EvaluateDailyQuestsUseCase.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt`
- Modify: `shared/src/commonTest/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCaseTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/questlog/domain/usecase/EvaluateDailyQuestsUseCaseTest.kt`

**Interfaces:**
- Consumes: `BlockedApp` (Task 2), `BlocklistRepository.current()` (Task 4), the `allowances` param (Task 5)
- Produces:
  - `CalculateDetoxRewardsUseCase(..., blockedApps: suspend () -> List<BlockedApp>, ...)` — `flaggedPackages: Set<String>` param removed
  - `EvaluateDailyQuestsUseCase(..., blockedApps: suspend () -> List<BlockedApp>, ...)` — `flaggedPackages: Set<String>` param removed

- [ ] **Step 1: Update `CalculateDetoxRewardsUseCase.kt`**

Add import:

```kotlin
import com.questlog.domain.model.BlockedApp
```

Change the constructor param (line ~35) from:

```kotlin
    private val flaggedPackages: Set<String>,
```

to:

```kotlin
    private val blockedApps: suspend () -> List<BlockedApp>,
```

In `invoke()`, replace the `fetchAndPersistToday` call (line ~48):

```kotlin
        val blocked = blockedApps()
        val savedMs = screenTimeRepo.fetchAndPersistToday(
            flaggedPackages = blocked.mapTo(mutableSetOf()) { it.packageName },
            startOfDayMs = startOfDay,
            allowances = blocked.associate { it.packageName to it.dailyLimitMs },
        )
```

- [ ] **Step 2: Update `EvaluateDailyQuestsUseCase.kt`**

Add import:

```kotlin
import com.questlog.domain.model.BlockedApp
```

Change the constructor param (line ~37) from:

```kotlin
    private val flaggedPackages: Set<String>,
```

to:

```kotlin
    private val blockedApps: suspend () -> List<BlockedApp>,
```

In `invoke()`, compute the set once near the top (after `val alreadyDone = ...`):

```kotlin
        val flaggedPackages = blockedApps().mapTo(mutableSetOf()) { it.packageName }
```

Change `isComplete` to take it as a parameter so the two window quests keep using it. Update the signature:

```kotlin
    private suspend fun isComplete(
        questId: String,
        todayKey: String,
        startOfDayMs: Long,
        nowMs: Long,
        today: LocalDate,
        flaggedPackages: Set<String>,
    ): Boolean = when (questId) {
```

and the call site inside the `for` loop:

```kotlin
            if (!isComplete(quest.id, todayKey, startOfDayMs, nowMs, today, flaggedPackages)) continue
```

The two `screenTimeRepo.flaggedForegroundInWindow(..., flaggedPackages)` uses inside `isComplete` now resolve to the parameter. `BUDGET_GUARDIAN` (`totalForegroundMs`) is unchanged.

- [ ] **Step 3: Update `SharedModule.kt` factories**

Remove the `import com.questlog.domain.model.defaultFlaggedPackages` line (no longer referenced). In the `EvaluateDailyQuestsUseCase` factory replace:

```kotlin
            flaggedPackages = defaultFlaggedPackages,
```

with:

```kotlin
            blockedApps = { get<BlocklistRepository>().current() },
```

In the `CalculateDetoxRewardsUseCase` factory replace:

```kotlin
            flaggedPackages = defaultFlaggedPackages,
```

with:

```kotlin
            blockedApps = { get<BlocklistRepository>().current() },
```

- [ ] **Step 4: Migrate `CalculateDetoxRewardsUseCaseTest.kt`**

Add near the top-level helpers:

```kotlin
import com.questlog.domain.model.BlockedApp

private fun blocked(vararg pkgs: String): suspend () -> List<BlockedApp> =
    { pkgs.map { BlockedApp(it, 0L) } }
```

Replace every `flaggedPackages = setOf("com.instagram.android")` and the positional `setOf("com.instagram.android")` in `useCaseFor` with `blockedApps = blocked("com.instagram.android")` / positional `blocked("com.instagram.android")`. Concretely:

- the `CalculateDetoxRewardsUseCase(...)` call in `invoke calculates rewards...` (line ~105): `blockedApps = blocked("com.instagram.android"),`
- the call in `invoke with high streak multiplier...` (line ~130): same
- the call in `repeated invocations on the same day...` (line ~150): same
- `useCaseFor` (line ~216–223):
  ```kotlin
      private fun useCaseFor(
          repo: ScreenTimeRepository,
          currencyRepo: CurrencyRepository,
          isPremium: () -> Boolean = { false },
      ) = CalculateDetoxRewardsUseCase(
          repo, currencyRepo, blocked("com.instagram.android"),
          isPremium = isPremium,
      )
  ```

Update `StubScreenTimeRepo.fetchAndPersistToday` (line ~87) to the 3-arg override:

```kotlin
    override suspend fun fetchAndPersistToday(
        flaggedPackages: Set<String>,
        startOfDayMs: Long,
        allowances: Map<String, Long>,
    ): Long {
        callCount++
        return savedMs
    }
```

Add one allowance-aware test:

```kotlin
    @Test
    fun `a generous allowance lets the day keep its full saved time`() = runTest {
        val currencyDao = FakeCurrencyDao()
        val screenTimeDao = FakeScreenTimeDao()
        val screenTimeRepo = ScreenTimeRepository(
            screenTimeDao,
            object : ScreenTimeTracker() {
                override suspend fun getUsageForPeriod(startMs: Long, endMs: Long) =
                    listOf(com.questlog.domain.model.AppUsage("com.insta", 10 * 60_000L))
                override fun isPermissionGranted() = true
            },
        )
        val useCase = CalculateDetoxRewardsUseCase(
            screenTimeRepo = screenTimeRepo,
            currencyRepo = CurrencyRepository(currencyDao),
            blockedApps = { listOf(com.questlog.domain.model.BlockedApp("com.insta", 60 * 60_000L)) },
        )

        val metrics = useCase()

        // 10 min usage, 60 min allowance -> nothing charged -> saved == elapsed-capped budget
        assertTrue(metrics.timeSavedMs > 0)
        assertEquals(metrics.timeSavedMs, currencyDao.balance.awardedSavedMsToday)
    }
```

- [ ] **Step 5: Migrate `EvaluateDailyQuestsUseCaseTest.kt`**

Add import + helper:

```kotlin
import com.questlog.domain.model.BlockedApp
```

At the stub repo (line ~87–92), update the `fetchAndPersistToday` override to the 3-arg form (same shape as Task 6 Step 4). Replace the `EvaluateDailyQuestsUseCase(...)` construction (line ~104–110) param `flaggedPackages = setOf(INSTAGRAM)` with:

```kotlin
            blockedApps = { listOf(BlockedApp(INSTAGRAM, 0L)) },
```

- [ ] **Step 6: Run the affected tests**

Run: `./gradlew :shared:desktopTest --no-daemon --rerun-tasks --tests 'com.questlog.domain.usecase.*'`
Expected: PASS — `DEEP_FOCUS_SHIELD` / `DAWN_DISCIPLINE` tests still key off the package set; `BUDGET_GUARDIAN` unaffected; the new allowance test passes.

- [ ] **Step 7: Full shared suite + app compile**

Run: `./gradlew :shared:desktopTest :app:assembleDebug --no-daemon --rerun-tasks`
Expected: all green — the Koin graph still resolves (`BlocklistRepository` is a `single` from Task 4).

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/domain/usecase/ shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt shared/src/commonTest/kotlin/com/questlog/domain/usecase/
git commit -m "Detox use cases read the live blocklist via a supplier"
```

---

## Task 7: `InstalledAppsProvider` (app, PackageManager)

**Files:**
- Create: `app/src/main/java/com/example/questlog/data/InstalledAppsProvider.kt`
- Modify: `app/src/main/java/com/example/questlog/QuestLogApp.kt` (Koin single)

**Interfaces:**
- Produces:
  - `data class InstalledApp(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable?)`
  - `interface InstalledAppsProvider { suspend fun launchableApps(): List<InstalledApp> }`
  - `class PackageManagerAppsProvider(context: Context) : InstalledAppsProvider`

- [ ] **Step 1: Create `data/InstalledAppsProvider.kt`**

```kotlin
package com.example.questlog.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

interface InstalledAppsProvider {
    /** Launchable apps on the device, minus our own package, sorted by label. */
    suspend fun launchableApps(): List<InstalledApp>
}

class PackageManagerAppsProvider(private val context: Context) : InstalledAppsProvider {

    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved.asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                InstalledApp(
                    packageName = pkg,
                    label = runCatching {
                        pm.getApplicationInfo(pkg, 0).let { pm.getApplicationLabel(it).toString() }
                    }.getOrDefault(pkg),
                    icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
```

- [ ] **Step 2: Register in Koin**

In `QuestLogApp.kt` `appModule`, add:

```kotlin
    single<com.example.questlog.data.InstalledAppsProvider> {
        com.example.questlog.data.PackageManagerAppsProvider(get())
    }
```

Koin Android's `get()` resolves the `Context` (already used via `androidContext(this@QuestLogApp)`).

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/data/InstalledAppsProvider.kt app/src/main/java/com/example/questlog/QuestLogApp.kt
git commit -m "Add InstalledAppsProvider (launchable apps via PackageManager)"
```

---

## Task 8: `QuestIcons.Settings` gear glyph

**Files:**
- Modify: `app/src/main/java/com/example/questlog/theme/QuestIcons.kt`

**Interfaces:**
- Produces: `QuestIcons.Settings: ImageVector`

- [ ] **Step 1: Add the glyph**

In `object QuestIcons`, after `Lock`:

```kotlin
    val Settings: ImageVector = line("Settings") {
        // hexagon-ish gear: outer ring + center dot
        moveTo(12f, 4f); lineTo(19f, 8f); lineTo(19f, 16f); lineTo(12f, 20f); lineTo(5f, 16f); lineTo(5f, 8f); close()
        moveTo(12f, 9f)
        arcTo(3f, 3f, 0f, true, true, 11.99f, 9f)
    }
```

- [ ] **Step 2: Add `QuestIcons.Settings` to the preview row**

In `IconsPreview`, add `QuestIcons.Settings` to the `listOf(...)`.

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`. Open `IconsPreview` in the IDE — a recognisable gear renders.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/theme/QuestIcons.kt
git commit -m "Add QuestIcons.Settings gear glyph"
```

---

## Task 9: `BlocklistViewModel`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/blocklist/BlocklistViewModel.kt`
- Modify: `app/src/main/java/com/example/questlog/QuestLogApp.kt` (Koin `viewModel`)
- Test: `app/src/test/java/com/example/questlog/ui/blocklist/BlocklistViewModelTest.kt`

**Interfaces:**
- Consumes: `BlocklistRepository` (Task 4), `InstalledAppsProvider` (Task 7), `ScreenTimeRepository` (existing Koin single, for `isPermissionGranted()`)
- Produces:
  - `data class AppRow(val packageName: String, val label: String, val icon: Drawable?, val blocked: Boolean, val dailyLimitMs: Long)`
  - `data class BlocklistUiState(val loading: Boolean, val permissionGranted: Boolean, val rows: List<AppRow>, val query: String)`
  - `sealed interface BlocklistIntent { ToggleBlocked(pkg); SetLimit(pkg, ms); SetQuery(q); RecheckPermission }`
  - `class BlocklistViewModel(...) : ViewModel()` with `val uiState: StateFlow<BlocklistUiState>` and `fun onIntent(i: BlocklistIntent)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/questlog/ui/blocklist/BlocklistViewModelTest.kt`:

```kotlin
package com.example.questlog.ui.blocklist

import com.example.questlog.data.InstalledApp
import com.example.questlog.data.InstalledAppsProvider
import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.repository.BlocklistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeBlocklistDao : BlocklistDao {
    val rows = mutableListOf<BlockedAppEntity>()
    private val flow = MutableStateFlow<List<BlockedAppEntity>>(emptyList())
    private fun emit() { flow.value = rows.sortedBy { it.packageName } }
    override fun observeAll(): Flow<List<BlockedAppEntity>> = flow
    override suspend fun getAll() = rows.sortedBy { it.packageName }
    override suspend fun get(packageName: String) = rows.firstOrNull { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) { rows.removeAll { it.packageName == app.packageName }; rows.add(app); emit() }
    override suspend fun delete(packageName: String) { rows.removeAll { it.packageName == packageName }; emit() }
}

private class FakeApps(private val apps: List<InstalledApp>) : InstalledAppsProvider {
    override suspend fun launchableApps() = apps
}

class BlocklistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        apps: List<InstalledApp> = listOf(
            InstalledApp("com.a", "Alpha", null),
            InstalledApp("com.b", "Bravo", null),
        ),
        dao: FakeBlocklistDao = FakeBlocklistDao(),
        permission: Boolean = true,
    ) = BlocklistViewModel(
        blocklistRepo = BlocklistRepository(dao),
        installedApps = FakeApps(apps),
        isUsageAccessGranted = { permission },
    )

    @Test
    fun `rows list every installed app with blocked reflecting the repo`() = runTest {
        val dao = FakeBlocklistDao().apply { rows.add(BlockedAppEntity("com.b", 0L)) }
        val model = vm(dao = dao)
        advanceUntilIdle()

        val state = model.uiState.first()
        assertFalse(state.loading)
        assertEquals(listOf("com.b", "com.a"), state.rows.map { it.packageName }) // blocked first, then A–Z
        assertTrue(state.rows.first { it.packageName == "com.b" }.blocked)
        assertFalse(state.rows.first { it.packageName == "com.a" }.blocked)
    }

    @Test
    fun `ToggleBlocked adds then removes the app`() = runTest {
        val model = vm()
        advanceUntilIdle()

        model.onIntent(BlocklistIntent.ToggleBlocked("com.a"))
        advanceUntilIdle()
        assertTrue(model.uiState.first().rows.first { it.packageName == "com.a" }.blocked)

        model.onIntent(BlocklistIntent.ToggleBlocked("com.a"))
        advanceUntilIdle()
        assertFalse(model.uiState.first().rows.first { it.packageName == "com.a" }.blocked)
    }

    @Test
    fun `SetLimit blocks the app with that allowance`() = runTest {
        val model = vm()
        advanceUntilIdle()

        model.onIntent(BlocklistIntent.SetLimit("com.a", 30 * 60_000L))
        advanceUntilIdle()

        val row = model.uiState.first().rows.first { it.packageName == "com.a" }
        assertTrue(row.blocked)
        assertEquals(30 * 60_000L, row.dailyLimitMs)
    }

    @Test
    fun `SetQuery filters rows by label case-insensitively`() = runTest {
        val model = vm()
        advanceUntilIdle()

        model.onIntent(BlocklistIntent.SetQuery("alp"))
        advanceUntilIdle()

        assertEquals(listOf("com.a"), model.uiState.first().rows.map { it.packageName })
    }

    @Test
    fun `permission flag comes from the checker and RecheckPermission refreshes it`() = runTest {
        var granted = false
        val model = BlocklistViewModel(
            blocklistRepo = BlocklistRepository(FakeBlocklistDao()),
            installedApps = FakeApps(emptyList()),
            isUsageAccessGranted = { granted },
        )
        advanceUntilIdle()
        assertFalse(model.uiState.first().permissionGranted)

        granted = true
        model.onIntent(BlocklistIntent.RecheckPermission)
        advanceUntilIdle()
        assertTrue(model.uiState.first().permissionGranted)
    }
}
```

- [ ] **Step 2: Run — verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks --tests 'com.example.questlog.ui.blocklist.BlocklistViewModelTest'`
Expected: FAIL — `BlocklistViewModel` unresolved.

- [ ] **Step 3: Write `BlocklistViewModel.kt`**

```kotlin
package com.example.questlog.ui.blocklist

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.data.InstalledAppsProvider
import com.questlog.data.repository.BlocklistRepository
import com.questlog.domain.model.BlockedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppRow(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val blocked: Boolean,
    val dailyLimitMs: Long,
)

data class BlocklistUiState(
    val loading: Boolean = true,
    val permissionGranted: Boolean = true,
    val rows: List<AppRow> = emptyList(),
    val query: String = "",
)

sealed interface BlocklistIntent {
    data class ToggleBlocked(val packageName: String) : BlocklistIntent
    data class SetLimit(val packageName: String, val dailyLimitMs: Long) : BlocklistIntent
    data class SetQuery(val query: String) : BlocklistIntent
    object RecheckPermission : BlocklistIntent
}

class BlocklistViewModel(
    private val blocklistRepo: BlocklistRepository,
    private val installedApps: InstalledAppsProvider,
    private val isUsageAccessGranted: () -> Boolean,
) : ViewModel() {

    private data class AppMeta(val packageName: String, val label: String, val icon: Drawable?)

    private val installed = MutableStateFlow<List<AppMeta>?>(null)
    private val query = MutableStateFlow("")
    private val permission = MutableStateFlow(isUsageAccessGranted())

    init {
        viewModelScope.launch {
            installed.value = installedApps.launchableApps()
                .map { AppMeta(it.packageName, it.label, it.icon) }
        }
    }

    val uiState: StateFlow<BlocklistUiState> =
        combine(
            installed,
            blocklistRepo.observeBlockedApps(),
            query,
            permission,
        ) { apps, blocked, q, granted ->
            if (apps == null) {
                BlocklistUiState(loading = true, permissionGranted = granted, query = q)
            } else {
                val byPkg: Map<String, BlockedApp> = blocked.associateBy { it.packageName }
                val rows = apps
                    .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
                    .map { meta ->
                        val b = byPkg[meta.packageName]
                        AppRow(
                            packageName = meta.packageName,
                            label = meta.label,
                            icon = meta.icon,
                            blocked = b != null,
                            dailyLimitMs = b?.dailyLimitMs ?: 0L,
                        )
                    }
                    .sortedWith(compareByDescending<AppRow> { it.blocked }.thenBy { it.label.lowercase() })
                BlocklistUiState(loading = false, permissionGranted = granted, rows = rows, query = q)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlocklistUiState())

    fun onIntent(intent: BlocklistIntent) {
        when (intent) {
            is BlocklistIntent.ToggleBlocked -> viewModelScope.launch {
                val currentlyBlocked = blocklistRepo.current().any { it.packageName == intent.packageName }
                blocklistRepo.setBlocked(intent.packageName, blocked = !currentlyBlocked)
            }
            is BlocklistIntent.SetLimit -> viewModelScope.launch {
                blocklistRepo.setLimit(intent.packageName, intent.dailyLimitMs)
            }
            is BlocklistIntent.SetQuery -> query.value = intent.query
            BlocklistIntent.RecheckPermission -> permission.value = isUsageAccessGranted()
        }
    }
}
```

- [ ] **Step 4: Run — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks --tests 'com.example.questlog.ui.blocklist.BlocklistViewModelTest'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Register in Koin**

In `QuestLogApp.kt` `appModule`:

```kotlin
    viewModel {
        com.example.questlog.ui.blocklist.BlocklistViewModel(
            blocklistRepo = get(),
            installedApps = get(),
            isUsageAccessGranted = { get<com.questlog.data.repository.ScreenTimeRepository>().isPermissionGranted() },
        )
    }
```

- [ ] **Step 6: Full app unit tests**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks`
Expected: all green (existing `DashboardViewModelTest`, `PremiumStatusProviderSeamTest` unaffected).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/blocklist/BlocklistViewModel.kt app/src/main/java/com/example/questlog/QuestLogApp.kt app/src/test/java/com/example/questlog/ui/blocklist/BlocklistViewModelTest.kt
git commit -m "Add BlocklistViewModel with app list, toggle, limit, search, permission"
```

---

## Task 10: `BlocklistRow` composable

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/blocklist/BlocklistRow.kt`

**Interfaces:**
- Consumes: `AppRow` (Task 9), `QuestLogTheme`, `QuestSpacing`, `QuestType`, `Pill`
- Produces: `@Composable fun BlocklistRow(row: AppRow, onToggle: () -> Unit, onSetLimit: (Long) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Create `BlocklistRow.kt`**

```kotlin
package com.example.questlog.ui.blocklist

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Pill

private val LIMIT_PRESETS_MS = listOf(0L, 15 * 60_000L, 30 * 60_000L, 60 * 60_000L)

private fun limitLabel(ms: Long): String = when (ms) {
    0L -> "Off"
    else -> {
        val m = ms / 60_000L
        if (m % 60 == 0L) "${m / 60}h" else "${m}m"
    }
}

@Composable
fun BlocklistRow(
    row: AppRow,
    onToggle: () -> Unit,
    onSetLimit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = QuestSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = row.icon?.let { runCatching { it.toBitmap() }.getOrNull() }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    painter = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(QuestIcons.Lock, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.size(QuestSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(row.label, style = QuestType.bodyLarge, color = c.inkPrimary)
                Text(row.packageName, style = QuestType.caption, color = c.inkMuted)
            }
            Switch(
                checked = row.blocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.ground,
                    checkedTrackColor = c.earned,
                    uncheckedTrackColor = c.surfaceRaised,
                    uncheckedBorderColor = c.rule,
                ),
            )
        }
        AnimatedVisibility(visible = row.blocked) {
            Column {
                Spacer(Modifier.height(QuestSpacing.sm))
                Text("DAILY LIMIT", style = QuestType.label, color = c.inkMuted)
                Spacer(Modifier.height(QuestSpacing.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
                    items(count = LIMIT_PRESETS_MS.size) { i ->
                        val ms = LIMIT_PRESETS_MS[i]
                        Pill(
                            text = limitLabel(ms),
                            filled = row.dailyLimitMs == ms,
                            onClick = { onSetLimit(ms) },
                        )
                    }
                }
            }
        }
    }
}

private fun previewRow(blocked: Boolean, limit: Long = 0L) = AppRow(
    packageName = "com.instagram.android", label = "Instagram", icon = null,
    blocked = blocked, dailyLimitMs = limit,
)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlocklistRowPreview() {
    QuestLogTheme {
        Column(Modifier.background(QuestLogTheme.colors.ground).padding(16.dp)) {
            BlocklistRow(previewRow(blocked = false), onToggle = {}, onSetLimit = {})
            BlocklistRow(previewRow(blocked = true, limit = 30 * 60_000L), onToggle = {}, onSetLimit = {})
        }
    }
}
```

> Note: `LazyRow` `items(count = ...)` avoids importing the list `items` overload; `androidx.compose.foundation.lazy.items` also works if preferred. Add `import androidx.compose.foundation.lazy.items` and use `items(LIMIT_PRESETS_MS)` if the reviewer prefers.
> The "Custom" preset from the spec is deferred to keep the row simple; the four presets (Off/15m/30m/1h) cover the mockup. If a custom field is wanted, add it in a follow-up.

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`. Open `BlocklistRowPreview` — an unblocked row (switch off) and a blocked row with "30m" chip filled, in light and dark.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/blocklist/BlocklistRow.kt
git commit -m "Add BlocklistRow: app row with toggle and limit-preset chips"
```

---

## Task 11: `BlocklistScreen` composable

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/blocklist/BlocklistScreen.kt`

**Interfaces:**
- Consumes: `BlocklistUiState`, `BlocklistIntent`, `AppRow` (Task 9); `BlocklistRow` (Task 10); `QuestScaffold`, `Hairline`, `QuestIcons`
- Produces: `@Composable fun BlocklistScreen(state: BlocklistUiState, onIntent: (BlocklistIntent) -> Unit, onBack: () -> Unit, onGrantAccess: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Create `BlocklistScreen.kt`**

```kotlin
package com.example.questlog.ui.blocklist

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold

@Composable
fun BlocklistScreen(
    state: BlocklistUiState,
    onIntent: (BlocklistIntent) -> Unit,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    QuestScaffold(
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary)
                }
                Text("Distractions", style = QuestType.screenTitle, color = c.inkPrimary)
            }
        },
    ) {
        Hairline()
        Spacer(Modifier.height(QuestSpacing.md))

        if (!state.permissionGranted) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(QuestShapes.medium)
                    .border(1.dp, c.locked, QuestShapes.medium)
                    .padding(QuestSpacing.md),
            ) {
                Text("Usage access needed", style = QuestType.bodyLarge, color = c.inkPrimary)
                Text(
                    "QuestLog needs usage access to measure time in these apps.",
                    style = QuestType.bodySmall, color = c.inkMuted,
                )
                Spacer(Modifier.height(QuestSpacing.sm))
                TextButton(onClick = onGrantAccess) {
                    Text("Grant access".uppercase(), style = QuestType.caption, color = c.locked)
                }
            }
            Spacer(Modifier.height(QuestSpacing.md))
        }

        // Search
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface)
                .padding(horizontal = QuestSpacing.md, vertical = QuestSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = { onIntent(BlocklistIntent.SetQuery(it)) },
                singleLine = true,
                textStyle = TextStyle(color = c.inkPrimary, fontSize = QuestType.bodyLarge.fontSize),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.earned),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text("Search apps", style = QuestType.bodyLarge, color = c.inkMuted)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(QuestSpacing.md))

        when {
            state.loading -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = c.earned, strokeWidth = 2.dp) }

            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(QuestSpacing.xs),
            ) {
                items(state.rows, key = { it.packageName }) { row ->
                    BlocklistRow(
                        row = row,
                        onToggle = { onIntent(BlocklistIntent.ToggleBlocked(row.packageName)) },
                        onSetLimit = { onIntent(BlocklistIntent.SetLimit(row.packageName, it)) },
                    )
                }
            }
        }

        Text(
            "Time in these apps beyond their limit is subtracted from your reclaimed time.",
            style = QuestType.caption, color = c.inkMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = QuestSpacing.md),
        )
    }
}

private fun previewState(permission: Boolean) = BlocklistUiState(
    loading = false,
    permissionGranted = permission,
    rows = listOf(
        AppRow("com.instagram.android", "Instagram", null, blocked = true, dailyLimitMs = 30 * 60_000L),
        AppRow("com.spotify.music", "Spotify", null, blocked = false, dailyLimitMs = 0L),
        AppRow("com.reddit.frontpage", "Reddit", null, blocked = true, dailyLimitMs = 0L),
    ),
)

@Preview(name = "Blocklist")
@Preview(name = "Blocklist dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlocklistScreenPreview() {
    QuestLogTheme {
        BlocklistScreen(previewState(permission = true), onIntent = {}, onBack = {}, onGrantAccess = {})
    }
}

@Preview(name = "Blocklist no permission")
@Composable
private fun BlocklistScreenNoPermissionPreview() {
    QuestLogTheme {
        BlocklistScreen(previewState(permission = false), onIntent = {}, onBack = {}, onGrantAccess = {})
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`. Open the three previews — list with a blocked+limited row, an unblocked row; the permission banner variant.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/blocklist/BlocklistScreen.kt
git commit -m "Add BlocklistScreen: search, permission banner, app list"
```

---

## Task 12: Navigation — gear entry + `Screen.Blocklist`

**Files:**
- Modify: `app/src/main/java/com/example/questlog/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt`

**Interfaces:**
- Consumes: `BlocklistScreen` (Task 11), `BlocklistViewModel` (Task 9), `BlocklistIntent`
- Produces: `TodayScreen` gains `onOpenBlocklist: () -> Unit`; `QuestLogRoot`'s `Screen` enum gains `Blocklist`

- [ ] **Step 1: Add `onOpenBlocklist` to `TodayScreen`**

In the `TodayScreen` signature add the parameter after `onOpenRealm`:

```kotlin
    onOpenRealm: () -> Unit,
    onOpenBlocklist: () -> Unit,
```

In the header's trailing `Row` (the one with the Pill + refresh `IconButton`), add a gear `IconButton` before the refresh one:

```kotlin
                    IconButton(
                        onClick = onOpenBlocklist,
                        modifier = Modifier.semantics { contentDescription = "Distractions" },
                    ) {
                        Icon(QuestIcons.Settings, contentDescription = null, tint = c.inkMuted)
                    }
```

Update the three `@Preview` composables' `TodayScreen(...)` calls to pass `onOpenBlocklist = {}`.

- [ ] **Step 2: Add `Screen.Blocklist` to `QuestLogRoot`**

Change the enum:

```kotlin
private enum class Screen { Today, Realm, Blocklist }
```

Change the `BackHandler`:

```kotlin
    BackHandler(enabled = screen != Screen.Today) { screen = Screen.Today }
```

In the `transitionSpec`, the existing `targetState == Screen.Realm` branch handles "forward" slide; generalize it to any non-Today target:

```kotlin
                } else if (targetState != Screen.Today) {
```

In the `when (s)` block add:

```kotlin
                    Screen.Blocklist -> {
                        val blocklistVm: com.example.questlog.ui.blocklist.BlocklistViewModel =
                            org.koin.compose.viewmodel.koinViewModel()
                        val blocklistState by blocklistVm.uiState.collectAsState()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                            blocklistVm.onIntent(
                                com.example.questlog.ui.blocklist.BlocklistIntent.RecheckPermission
                            )
                            onPauseOrDispose { }
                        }
                        com.example.questlog.ui.blocklist.BlocklistScreen(
                            state = blocklistState,
                            onIntent = blocklistVm::onIntent,
                            onBack = { screen = Screen.Today },
                            onGrantAccess = {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                        )
                    }
```

Wire `onOpenBlocklist` on the `Screen.Today` branch:

```kotlin
                    Screen.Today -> TodayScreen(
                        state = state,
                        onRefresh = { viewModel.onIntent(DashboardIntent.Refresh) },
                        onOpenPaywall = { viewModel.onIntent(DashboardIntent.OpenPaywall) },
                        onOpenRealm = { screen = Screen.Realm },
                        onOpenBlocklist = { screen = Screen.Blocklist },
                    )
```

- [ ] **Step 3: Compile + full unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :shared:desktopTest --no-daemon --rerun-tasks`
Expected: all `BUILD SUCCESSFUL` / green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/today/TodayScreen.kt app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt
git commit -m "Wire the blocklist screen: gear in Today header, Screen.Blocklist"
```

---

## Task 13: Instrumented `BlocklistScreenTest` (not in CI)

**Files:**
- Create: `app/src/androidTest/java/com/example/questlog/ui/BlocklistScreenTest.kt`

**Interfaces:**
- Consumes: `BlocklistScreen`, `BlocklistUiState`, `AppRow`, `BlocklistIntent`

- [ ] **Step 1: Create the test**

```kotlin
package com.example.questlog.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.blocklist.AppRow
import com.example.questlog.ui.blocklist.BlocklistIntent
import com.example.questlog.ui.blocklist.BlocklistScreen
import com.example.questlog.ui.blocklist.BlocklistUiState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class BlocklistScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun state(permission: Boolean = true) = BlocklistUiState(
        loading = false,
        permissionGranted = permission,
        rows = listOf(
            AppRow("com.a", "Alpha", null, blocked = false, dailyLimitMs = 0L),
            AppRow("com.b", "Bravo", null, blocked = true, dailyLimitMs = 0L),
        ),
    )

    @Test
    fun toggling_a_row_emits_ToggleBlocked() {
        val intents = mutableListOf<BlocklistIntent>()
        rule.setContent {
            QuestLogTheme {
                BlocklistScreen(state(), onIntent = { intents.add(it) }, onBack = {}, onGrantAccess = {})
            }
        }
        // The Alpha row's switch is off; Bravo's is on.
        rule.onAllNodesWithTag_orSwitchFallback() // placeholder — see note
    }

    @Test
    fun permission_banner_shows_when_access_missing() {
        rule.setContent {
            QuestLogTheme {
                BlocklistScreen(state(permission = false), onIntent = {}, onBack = {}, onGrantAccess = {})
            }
        }
        rule.onNodeWithText("Usage access needed").assertExists()
    }

    @Test
    fun no_banner_when_access_granted() {
        rule.setContent {
            QuestLogTheme {
                BlocklistScreen(state(permission = true), onIntent = {}, onBack = {}, onGrantAccess = {})
            }
        }
        rule.onNodeWithText("Usage access needed").assertDoesNotExist()
    }
}
```

> The switch-interaction assertion needs a test tag. When implementing, add
> `Modifier.testTag("switch_${row.packageName}")` to the `Switch` in `BlocklistRow`
> and replace the placeholder with:
> ```kotlin
> rule.onNodeWithTag("switch_com.a").assertIsOff().performClick()
> assertEquals(BlocklistIntent.ToggleBlocked("com.a"), intents.single())
> ```
> Add `import androidx.compose.ui.test.onNodeWithTag` and `import androidx.compose.ui.platform.testTag`.

- [ ] **Step 2: Implement the test tag in `BlocklistRow`**

Add `import androidx.compose.ui.platform.testTag` and put `Modifier.testTag("switch_${row.packageName}")` on the `Switch`. Finalize the first test per the note above (remove the placeholder line).

- [ ] **Step 3: Run locally on an emulator (optional — not in CI)**

Run: `./gradlew :app:connectedDebugAndroidTest --no-daemon` (needs a running emulator)
Expected: 3 tests pass. If no emulator is available, skip — CI does not run this.

- [ ] **Step 4: Compile the androidTest source at least**

Run: `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/example/questlog/ui/BlocklistScreenTest.kt app/src/main/java/com/example/questlog/ui/blocklist/BlocklistRow.kt
git commit -m "Add instrumented BlocklistScreenTest (not run in CI)"
```

---

## Task 14: Docs

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: `CHANGELOG.md` — under `## [Unreleased]` → `### Added`**

```markdown
- **In-app blocklist editor** — a gear on the Today screen opens **Distractions**, a
  searchable list of installed apps. Toggle any app as a distraction and optionally
  give it a daily allowance (Off / 15m / 30m / 1h); only foreground time beyond the
  allowance is subtracted from reclaimed time. Seeded on first run with the seven
  historical defaults (Room migration `v7→v8` for existing installs, an `onCreate`
  callback for fresh ones). The screen surfaces a usage-access permission prompt when
  the permission is missing.
```

- [ ] **Step 2: `README.md`**

- In the two-screen description, note the third screen:
  > The `app` UI is two screens — **Today** and **Realm** — plus **Distractions** (the
  > blocklist editor, reached from the Today gear) and the Pro paywall dialog, hosted by
  > `ui/QuestLogRoot.kt` with no navigation library.
- In the `domain/model` row of the layer table, add `BlockedApp`.
- In the `data/repository` row, add `BlocklistRepository`.
- In the `data/local` row, bump "4 entities / DAOs" to "5 entities / DAOs" and note `blocked_app`.

- [ ] **Step 3: `CLAUDE.md`**

- Under **Invariants / gotchas**, add:
  > - The distraction list is the `blocked_app` table (`BlocklistRepository`), seeded with
  >   `defaultFlaggedPackages` (now in `domain/model/DefaultBlocklist.kt`) via `MIGRATION_7_8`
  >   and the `questLogSeedCallback` on fresh installs. The detox use cases read it live
  >   through a `suspend () -> List<BlockedApp>` supplier; per-app `dailyLimitMs` is an
  >   allowance — only overage counts (`DetoxBudget.chargeableMs`).
- Under **Testing patterns**, add:
  > - `BlocklistDaoTest` builds the in-memory DB with `.addCallback(questLogSeedCallback)` to
  >   exercise the fresh-install seed.

- [ ] **Step 4: Final full verification**

Run:
```bash
./gradlew :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug --no-daemon --rerun-tasks
```
Expected: all green / `BUILD SUCCESSFUL`. Confirm `git status` shows `shared/schemas/com.questlog.data.local.QuestLogDatabase/8.json` committed (Task 3).

- [ ] **Step 5: Commit**

```bash
git add README.md CHANGELOG.md CLAUDE.md
git commit -m "Docs: in-app blocklist editor"
```

---

## Deferred (not in this plan)

- A general **Settings** screen — the gear opens the blocklist directly.
- **Immediate** dashboard recalc on a blocklist edit — the next `DetoxMonitorFlow`
  tick (~60s) or a manual refresh picks up the change. Wiring a cross-ViewModel
  refresh trigger is out of scope; note it as a known ~1-minute lag.
- A **Custom** limit value (free-form minutes) beyond the four presets.
- Per-app usage stats shown in the row (the mockup's "avg 1h 45m/day").
- Notifications / enforcement when an app passes its limit.

### Known trade-offs accepted at merge (final-review findings, deliberately not fixed)

- ~~**The detox reward becomes farmable.**~~ Fixed in a follow-up
  (`fix/blocklist-farmable-reward`): `fetchAndPersistToday` now charges the union
  of the current blocklist and every app with a `screen_time_records` row for
  today, so unblocking mid-day hands nothing back (a since-unblocked app is
  charged in full). Resets at midnight; no schema change.
- ~~**Rows re-sort under the finger.**~~ Fixed in a follow-up
  (`fix/blocklist-stable-sort`): the row order is frozen in a dedicated `order`
  flow, recomputed only on screen entry via `BlocklistIntent.Regroup`; toggles
  flip the switch in place.
- ~~**`BUDGET_GUARDIAN` counts stale `screen_time_records`.**~~ Resolved by the
  farmable-reward fix: `fetchAndPersistToday` keeps rows for since-unblocked apps
  live, and `totalForegroundMs` (what `BUDGET_GUARDIAN` reads) sums exactly the
  same set the reward charges — quest and reward are now consistent, and the rows
  are current rather than frozen. An app blocked at any tick today counts toward
  the quest for the rest of the day, by design; self-heals at midnight.
- ~~**`expect`/`actual` `open` asymmetry on `ScreenTimeTracker`.**~~ Fixed in
  `chore/blocklist-deferred-cleanup`: the `expect` declaration and the Android
  `actual` are now `open`, matching the desktop `actual`. Tests subclass it as a
  stub on any target; production never does.

## Self-Review

- **Spec coverage:** app list + toggle + persist (T3–T4, T9–T12); seed defaults (T3); usage-access banner (T9, T11, T12); per-app allowance overage (T1, T5, T6); gear → dedicated screen (T8, T12); quest semantics unchanged (T6 Step 5); schema `8.json` (T3); tests: `commonTest` for `chargeableMs` / repo / use cases, `desktopTest` for migration + DAO, instrumented not-in-CI (T13). All covered.
- **Placeholder scan:** the only intentional placeholder is `BlocklistScreenTest` Step 1's switch assertion, resolved explicitly in Step 2 with the exact code and imports.
- **Type consistency:** `blockedApps: suspend () -> List<BlockedApp>` used identically in T6 for both use cases and their tests; `fetchAndPersistToday(flaggedPackages, startOfDayMs, allowances)` 3-arg form consistent across T5 (impl), T6 (stub overrides); `BlocklistRepository` method names (`observeBlockedApps`, `current`, `setBlocked`, `setLimit`) consistent T4/T9; `BlocklistIntent` variants consistent T9/T12/T13.
