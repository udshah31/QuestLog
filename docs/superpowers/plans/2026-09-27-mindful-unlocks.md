# Mindful Unlocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An "Open mindfully" flow in QuestLog: pick a distraction app, say why, a TypeSafe judgment (via our Cloudflare Worker proxy) decides whether it's a bounded task, and a purposeful answer grants 5 minutes of reward-free grace on that app for today before QuestLog launches it.

**Architecture:** `shared` gets a pure grant rule, a `mindful_unlock` table (migration 9→10), a repository, and a `graceToday` supplier that `CalculateDetoxRewardsUseCase` adds to each app's allowance. `app` gets an `IntentJudge` HTTP client, an `UnlockViewModel` + `UnlockScreen` reached from Today, and a new Pro perk. `proxy/` is a standalone Cloudflare Worker holding the TypeSafe key.

**Tech Stack:** Kotlin Multiplatform, Room KMP 2.8.4, Koin, Jetpack Compose, kotlinx-serialization-json (already in the catalog), `HttpURLConnection`; Cloudflare Workers (JavaScript) + vitest.

**Spec:** `docs/superpowers/specs/2026-09-27-mindful-unlocks-design.md`

## Global Constraints

- Grace: `GRACE_MS = 5 * 60_000L`; granted iff `purposeful >= 0.7` **and** category ∉ `{boredom, habit, unclear}`.
- Free players: `FREE_UNLOCKS_PER_DAY = 1` judged unlock/day; Pro unlimited. A failed check (`Unavailable`) records nothing and doesn't consume it.
- The typed reason is **never stored** (not in Room, not in prefs, not logged by the proxy).
- Streak logic is untouched; grace only enters the reward's allowance map.
- DB goes 9 → 10. Migration `CREATE TABLE` must match the exported `createSql` in `shared/schemas/.../10.json` **exactly**. Commit `10.json` only; never use `--rerun-tasks` on the shared module in this plan (it rewrites every `N.json`).
- No new dependencies in `app`/`shared` beyond adding the existing catalog entry `libs.kotlinx.serialization.json` to `app`. Proxy dev-dep: `vitest` only.
- Colour only via `QuestLogTheme.colors`. Copy (verbatim): row "Open an app mindfully"; prompt "What are you opening {App} for?"; button "Check" / "Checking…"; granted "5 minutes, no charge." + "Open {App}"; drifting "That sounds like drifting — it'll count today." + "Open anyway" / "Not now"; unavailable "Couldn't check right now." + "Open anyway"; quota "1 free mindful unlock left today" / "Unlimited with Pro"; privacy "Your answer is checked by AI and not saved."; snackbar "Can't open that app."; perk `Perk("✦", "Mindful Unlocks", "Say why, get 5 minutes free — as often as you like")`.
- Proxy URL: `BuildConfig.UNLOCK_PROXY_URL` from env `UNLOCK_PROXY_URL` or `keystore.properties` `unlockProxyUrl`, placeholder `REPLACE_WITH_UNLOCK_PROXY_URL` → every judgment `Unavailable`.
- CI runs `:shared:desktopTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`; all stay green. `--no-daemon` on every Gradle command.

## Review Focus

1. **Double-tap "Check"** — a second Check while one is in flight must not make a second proxy call or record two unlocks. Test in Task 6.
2. **Free cap reached, then Pro bought** — after purchase the next Check must go through without a paywall (isPremium is read live, not cached at VM creation). Test in Task 6.
3. **Unlocks on two apps the same day** — grace sums per package; Instagram's grace never reduces TikTok's charge. Test in Task 2 (DAO sum by package) and Task 4 (allowance per package).
4. **Proxy returns junk** — `{"purposeful": "high"}`, missing `category`, non-JSON, HTML error page → `Unavailable`, never a crash or a grant. Test in Task 5.
5. **Coming back to the Unlock screen after a finished unlock** — it must start fresh at the app list, not show the old verdict. Test in Task 6 (`Reset`).

---

## File map

| File | Change |
|---|---|
| `shared/src/commonMain/kotlin/com/questlog/domain/unlock/MindfulUnlockRule.kt` | **Create** |
| `shared/src/commonTest/kotlin/com/questlog/domain/unlock/MindfulUnlockRuleTest.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/data/local/entity/MindfulUnlockEntity.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/data/local/dao/MindfulUnlockDao.kt` | **Create** |
| `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogDatabase.kt` | **Modify** — entity, DAO, version 10 |
| `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogMigrations.kt` | **Modify** — `MIGRATION_9_10` |
| `shared/schemas/com.questlog.data.local.QuestLogDatabase/10.json` | **Generated**, commit |
| `shared/src/androidMain/kotlin/com/questlog/di/PlatformModule.android.kt` | **Modify** — DAO single |
| `shared/src/desktopTest/kotlin/com/questlog/data/local/MindfulUnlockDaoTest.kt` | **Create** |
| `shared/src/desktopTest/kotlin/com/questlog/data/local/ScreenTimeMigrationTest.kt` | **Modify** — 9→10 test |
| `shared/src/commonMain/kotlin/com/questlog/data/repository/MindfulUnlockRepository.kt` | **Create** |
| `shared/src/commonTest/kotlin/com/questlog/data/repository/MindfulUnlockRepositoryTest.kt` | **Create** (+ `FakeMindfulUnlockDao`) |
| `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt` | **Modify** |
| `shared/src/commonMain/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCase.kt` | **Modify** — `graceToday` |
| `shared/src/commonTest/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCaseTest.kt` | **Modify** |
| `app/build.gradle.kts` | **Modify** — `UNLOCK_PROXY_URL`, serialization-json |
| `app/src/main/java/com/example/questlog/unlock/IntentJudge.kt` | **Create** — `Verdict`, `IntentJudge`, `parseVerdict`, `InstallId` |
| `app/src/test/java/com/example/questlog/unlock/IntentJudgeTest.kt` | **Create** |
| `app/src/main/java/com/example/questlog/ui/unlock/UnlockViewModel.kt` | **Create** |
| `app/src/test/java/com/example/questlog/ui/unlock/UnlockViewModelTest.kt` | **Create** |
| `app/src/main/java/com/example/questlog/ui/unlock/UnlockScreen.kt` | **Create** |
| `app/src/main/java/com/example/questlog/ui/today/MindfulUnlockRow.kt` | **Create** |
| `app/src/main/java/com/example/questlog/ui/today/TodayScreen.kt` | **Modify** — row + `onOpenUnlock` |
| `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt` | **Modify** — `Screen.Unlock` |
| `app/src/main/java/com/example/questlog/ui/paywall/PaywallScreen.kt` | **Modify** — perk |
| `app/src/main/java/com/example/questlog/QuestLogApp.kt` | **Modify** — Koin |
| `proxy/src/index.js`, `proxy/wrangler.toml`, `proxy/package.json`, `proxy/package-lock.json`, `proxy/test/index.test.js` | **Create** |
| `.gitignore`, `.github/workflows/deploy-internal.yml`, `README.md`, `CLAUDE.md` | **Modify** (Task 8/9) |

---

### Task 1: Grant rule

**Files:**
- Create: `shared/src/commonMain/kotlin/com/questlog/domain/unlock/MindfulUnlockRule.kt`
- Test: `shared/src/commonTest/kotlin/com/questlog/domain/unlock/MindfulUnlockRuleTest.kt`

**Interfaces:**
- Produces: `object MindfulUnlockRule { const val GRACE_MS: Long; const val PURPOSEFUL_THRESHOLD: Double; const val FREE_UNLOCKS_PER_DAY: Int; fun graceFor(purposeful: Double, category: String): Long }` in package `com.questlog.domain.unlock`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.questlog.domain.unlock

import com.questlog.domain.unlock.MindfulUnlockRule.GRACE_MS
import com.questlog.domain.unlock.MindfulUnlockRule.graceFor
import kotlin.test.Test
import kotlin.test.assertEquals

class MindfulUnlockRuleTest {
    @Test fun `purposeful task categories at the threshold earn grace`() {
        for (c in listOf("message", "create", "lookup", "work")) assertEquals(GRACE_MS, graceFor(0.7, c), c)
    }

    @Test fun `just under the threshold earns nothing`() = assertEquals(0L, graceFor(0.69, "message"))

    @Test fun `drifting categories earn nothing however purposeful`() {
        for (c in listOf("boredom", "habit", "unclear")) assertEquals(0L, graceFor(0.99, c), c)
    }

    @Test fun `grace is five minutes`() = assertEquals(5 * 60_000L, GRACE_MS)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.domain.unlock.MindfulUnlockRuleTest"`
Expected: FAIL — compilation, `MindfulUnlockRule` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.questlog.domain.unlock

/** Decides the reward-free grace a judged Mindful Unlock earns. Pure. */
object MindfulUnlockRule {
    const val GRACE_MS = 5 * 60_000L
    /** The one tuning knob: Noul probability that the reason is a bounded task. */
    const val PURPOSEFUL_THRESHOLD = 0.7
    const val FREE_UNLOCKS_PER_DAY = 1

    private val DRIFTING = setOf("boredom", "habit", "unclear")

    fun graceFor(purposeful: Double, category: String): Long =
        if (purposeful >= PURPOSEFUL_THRESHOLD && category !in DRIFTING) GRACE_MS else 0L
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.domain.unlock.MindfulUnlockRuleTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/questlog/domain/unlock shared/src/commonTest/kotlin/com/questlog/domain/unlock
git commit -m "Mindful Unlocks: grace rule"
```

---

### Task 2: `mindful_unlock` table, DAO, migration 9 → 10

**Files:**
- Create: `shared/src/commonMain/kotlin/com/questlog/data/local/entity/MindfulUnlockEntity.kt`
- Create: `shared/src/commonMain/kotlin/com/questlog/data/local/dao/MindfulUnlockDao.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogDatabase.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/data/local/QuestLogMigrations.kt`
- Modify: `shared/src/androidMain/kotlin/com/questlog/di/PlatformModule.android.kt`
- Create (generated): `shared/schemas/com.questlog.data.local.QuestLogDatabase/10.json`
- Test: `shared/src/desktopTest/kotlin/com/questlog/data/local/MindfulUnlockDaoTest.kt`, `ScreenTimeMigrationTest.kt`

**Interfaces:**
- Produces:
  - `data class MindfulUnlockEntity(id: Long = 0, date: String, packageName: String, category: String, purposeful: Double, graceMs: Long, createdAt: Long)` (table `mindful_unlock`)
  - `data class PackageGrace(val packageName: String, val graceMs: Long)` (in `MindfulUnlockDao.kt`)
  - `interface MindfulUnlockDao { suspend fun insert(row: MindfulUnlockEntity): Long; suspend fun graceByPackageForDate(date: String): List<PackageGrace>; suspend fun countForDate(date: String): Int }`
  - `QuestLogDatabase.mindfulUnlockDao()`, `MIGRATION_9_10`, DB version 10.

- [ ] **Step 1: Entity + DAO + DB wiring (needed for the test to compile)**

`MindfulUnlockEntity.kt`:

```kotlin
package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One judged Mindful Unlock. [graceMs] is what it earned (0 if not purposeful).
 * The player's typed reason is deliberately not stored.
 */
@Entity(tableName = "mindful_unlock", indices = [Index("date")])
data class MindfulUnlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val packageName: String,
    val category: String,
    val purposeful: Double,
    val graceMs: Long,
    val createdAt: Long,
)
```

`MindfulUnlockDao.kt`:

```kotlin
package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.questlog.data.local.entity.MindfulUnlockEntity

data class PackageGrace(val packageName: String, val graceMs: Long)

@Dao
interface MindfulUnlockDao {
    @Insert
    suspend fun insert(row: MindfulUnlockEntity): Long

    @Query("SELECT packageName, SUM(graceMs) AS graceMs FROM mindful_unlock WHERE date = :date GROUP BY packageName")
    suspend fun graceByPackageForDate(date: String): List<PackageGrace>

    @Query("SELECT COUNT(*) FROM mindful_unlock WHERE date = :date")
    suspend fun countForDate(date: String): Int
}
```

`QuestLogDatabase.kt`: add `MindfulUnlockEntity::class` to `entities`, set `version = 10`, add `abstract fun mindfulUnlockDao(): MindfulUnlockDao` (with imports).

`QuestLogMigrations.kt` — add after `MIGRATION_8_9`:

```kotlin
/** v10: judged Mindful Unlocks (grace per app per day). No reason text is stored. */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `mindful_unlock` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                "`packageName` TEXT NOT NULL, `category` TEXT NOT NULL, `purposeful` REAL NOT NULL, " +
                "`graceMs` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_mindful_unlock_date` ON `mindful_unlock` (`date`)")
    }
}
```

and append `MIGRATION_9_10` to `questLogMigrations`.

`PlatformModule.android.kt` — add `single { get<com.questlog.data.local.QuestLogDatabase>().mindfulUnlockDao() }` after the `blocklistDao()` line.

- [ ] **Step 2: Build to export `10.json`, check the DDL**

Run: `./gradlew :shared:desktopTestClasses --no-daemon`
Then: `grep -n "createSql\|index_mindful" shared/schemas/com.questlog.data.local.QuestLogDatabase/10.json | grep -i mindful`
Expected: `createSql` for `mindful_unlock` is `CREATE TABLE IF NOT EXISTS \`${TABLE_NAME}\` (\`id\` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, \`date\` TEXT NOT NULL, \`packageName\` TEXT NOT NULL, \`category\` TEXT NOT NULL, \`purposeful\` REAL NOT NULL, \`graceMs\` INTEGER NOT NULL, \`createdAt\` INTEGER NOT NULL)` and the index is `CREATE INDEX IF NOT EXISTS \`index_mindful_unlock_date\` ON \`${TABLE_NAME}\` (\`date\`)`. If Room's text differs in any way, change `MIGRATION_9_10` to match it exactly (with `mindful_unlock` for `${TABLE_NAME}`). Run `git status shared/schemas` — only `10.json` may be new/changed; `git checkout` any other `N.json` that changed.

- [ ] **Step 3: Write the failing tests**

`MindfulUnlockDaoTest.kt`:

```kotlin
package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.dao.PackageGrace
import com.questlog.data.local.entity.MindfulUnlockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MindfulUnlockDaoTest {
    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    private val dao = db.mindfulUnlockDao()

    @AfterTest fun tearDown() = db.close()

    private fun row(date: String, pkg: String, grace: Long) =
        MindfulUnlockEntity(date = date, packageName = pkg, category = "message", purposeful = 0.9, graceMs = grace, createdAt = 0L)

    @Test
    fun `grace sums per package for the date only`() = runTest {
        dao.insert(row("2026-09-27", "com.insta", 300_000))
        dao.insert(row("2026-09-27", "com.insta", 300_000))
        dao.insert(row("2026-09-27", "com.tiktok", 0))
        dao.insert(row("2026-09-26", "com.insta", 300_000))

        assertEquals(
            setOf(PackageGrace("com.insta", 600_000), PackageGrace("com.tiktok", 0)),
            dao.graceByPackageForDate("2026-09-27").toSet(),
        )
    }

    @Test
    fun `count covers every judged unlock of the date, granted or not`() = runTest {
        dao.insert(row("2026-09-27", "com.insta", 300_000))
        dao.insert(row("2026-09-27", "com.tiktok", 0))
        dao.insert(row("2026-09-26", "com.insta", 300_000))
        assertEquals(2, dao.countForDate("2026-09-27"))
        assertEquals(0, dao.countForDate("2026-09-25"))
    }
}
```

In `ScreenTimeMigrationTest.kt`, after the `8 to 9` test, add:

```kotlin
    @Test
    fun `9 to 10 creates mindful_unlock`() = runTest {
        helper.createDatabase(9).close()

        val v10 = helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10))

        v10.execSQL(
            "INSERT INTO mindful_unlock (date, packageName, category, purposeful, graceMs, createdAt) " +
                "VALUES ('2026-09-27', 'com.insta', 'message', 0.9, 300000, 0)"
        )
        assertEquals(300_000L, v10.queryLongs("SELECT graceMs FROM mindful_unlock").single().single())
        v10.close()
    }
```

and update the full-chain test only if it asserts the final version (it targets 7 — leave it).

- [ ] **Step 4: Run to verify**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.data.local.*"`
Expected: PASS — including `MindfulUnlockDaoTest` (2) and `9 to 10 creates mindful_unlock`. (The DAO test compiles only after Step 1; its red state is the Step-2 DDL check. If `runMigrationsAndValidate` fails, the migration SQL doesn't match `10.json` — fix the SQL, not the test.)

- [ ] **Step 5: Commit**

```bash
git add shared/src shared/schemas/com.questlog.data.local.QuestLogDatabase/10.json
git commit -m "Mindful Unlocks: mindful_unlock table, DAO, migration 9 to 10"
```

---

### Task 3: `MindfulUnlockRepository` + DI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/questlog/data/repository/MindfulUnlockRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/questlog/data/repository/MindfulUnlockRepositoryTest.kt`

**Interfaces:**
- Consumes: `MindfulUnlockDao`, `PackageGrace`, `MindfulUnlockEntity` (Task 2); `MindfulUnlockRule.graceFor` (Task 1).
- Produces: `class MindfulUnlockRepository(dao: MindfulUnlockDao, clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault())` with `suspend fun record(packageName: String, category: String, purposeful: Double): Long` (returns grace granted), `suspend fun graceMsToday(): Map<String, Long>`, `suspend fun countToday(): Int`. Also `class FakeMindfulUnlockDao : MindfulUnlockDao` in the test file (public, reused by app tests only via their own copy).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.questlog.data.repository

import com.questlog.data.local.dao.MindfulUnlockDao
import com.questlog.data.local.dao.PackageGrace
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.domain.unlock.MindfulUnlockRule.GRACE_MS
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Models Room: insert assigns ids; queries filter by date and group by package. */
class FakeMindfulUnlockDao : MindfulUnlockDao {
    val rows = mutableListOf<MindfulUnlockEntity>()
    override suspend fun insert(row: MindfulUnlockEntity): Long {
        val id = rows.size + 1L
        rows += row.copy(id = id)
        return id
    }
    override suspend fun graceByPackageForDate(date: String): List<PackageGrace> =
        rows.filter { it.date == date }.groupBy { it.packageName }
            .map { (pkg, rs) -> PackageGrace(pkg, rs.sumOf { it.graceMs }) }
    override suspend fun countForDate(date: String): Int = rows.count { it.date == date }
}

private class FixedClock(private val at: Instant) : Clock { override fun now() = at }

class MindfulUnlockRepositoryTest {
    // 2026-09-27T10:00Z, read in UTC
    private val clock = FixedClock(Instant.parse("2026-09-27T10:00:00Z"))

    @Test
    fun `record stores today's date and the rule's grace, and returns it`() = runTest {
        val dao = FakeMindfulUnlockDao()
        val repo = MindfulUnlockRepository(dao, clock, TimeZone.UTC)

        assertEquals(GRACE_MS, repo.record("com.insta", "message", 0.9))
        assertEquals(0L, repo.record("com.insta", "boredom", 0.9))

        assertEquals(listOf("2026-09-27", "2026-09-27"), dao.rows.map { it.date })
        assertEquals(listOf(GRACE_MS, 0L), dao.rows.map { it.graceMs })
    }

    @Test
    fun `grace and count are today's only`() = runTest {
        val dao = FakeMindfulUnlockDao().apply {
            rows += MindfulUnlockEntity(1, "2026-09-26", "com.insta", "message", 0.9, GRACE_MS, 0)
        }
        val repo = MindfulUnlockRepository(dao, clock, TimeZone.UTC)
        repo.record("com.insta", "message", 0.9)
        repo.record("com.tiktok", "lookup", 0.8)

        assertEquals(mapOf("com.insta" to GRACE_MS, "com.tiktok" to GRACE_MS), repo.graceMsToday())
        assertEquals(2, repo.countToday())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.data.repository.MindfulUnlockRepositoryTest"`
Expected: FAIL — `MindfulUnlockRepository` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.questlog.data.repository

import com.questlog.data.local.dao.MindfulUnlockDao
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.domain.unlock.MindfulUnlockRule
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MindfulUnlockRepository(
    private val dao: MindfulUnlockDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private fun today(): String = clock.now().toLocalDateTime(timeZone).date.toString()

    /** Records a judged unlock and returns the grace it earned (0 if not purposeful). */
    suspend fun record(packageName: String, category: String, purposeful: Double): Long {
        val grace = MindfulUnlockRule.graceFor(purposeful, category)
        dao.insert(
            MindfulUnlockEntity(
                date = today(),
                packageName = packageName,
                category = category,
                purposeful = purposeful,
                graceMs = grace,
                createdAt = clock.now().toEpochMilliseconds(),
            ),
        )
        return grace
    }

    suspend fun graceMsToday(): Map<String, Long> =
        dao.graceByPackageForDate(today()).associate { it.packageName to it.graceMs }

    suspend fun countToday(): Int = dao.countForDate(today())
}
```

`SharedModule.kt`: import `com.questlog.data.repository.MindfulUnlockRepository`; add `single { MindfulUnlockRepository(get()) }` after `BlocklistRepository`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.data.repository.MindfulUnlockRepositoryTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "Mindful Unlocks: repository and DI"
```

---

### Task 4: Grace reaches the reward

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCase.kt:33-56`
- Modify: `shared/src/commonMain/kotlin/com/questlog/di/SharedModule.kt` (the `CalculateDetoxRewardsUseCase` factory)
- Test: `shared/src/commonTest/kotlin/com/questlog/domain/usecase/CalculateDetoxRewardsUseCaseTest.kt`

**Interfaces:**
- Consumes: `MindfulUnlockRepository.graceMsToday()` (Task 3).
- Produces: `CalculateDetoxRewardsUseCase(..., graceToday: suspend () -> Map<String, Long> = { emptyMap() }, clock: Clock = Clock.System)` — new param placed **before** `clock`.

- [ ] **Step 1: Write the failing tests** (add to the test class)

```kotlin
    @Test
    fun `mindful grace is charged like extra daily limit for that app only`() = runTest {
        val usage = listOf(AppUsage("com.insta", 10 * 60_000L), AppUsage("com.tiktok", 10 * 60_000L))
        val tz = TimeZone.currentSystemDefault()
        val noon = Clock.System.now().toLocalDateTime(tz).date.atTime(12, 0).toInstant(tz)

        suspend fun savedWith(grace: Map<String, Long>): Long {
            val currencyDao = FakeCurrencyDao()
            CalculateDetoxRewardsUseCase(
                screenTimeRepo = ScreenTimeRepository(
                    FakeScreenTimeDao(),
                    object : ScreenTimeTracker() {
                        override suspend fun getUsageForPeriod(startMs: Long, endMs: Long) = usage
                        override fun isPermissionGranted() = true
                    },
                ),
                currencyRepo = CurrencyRepository(currencyDao),
                blockedApps = blocked("com.insta", "com.tiktok"),
                graceToday = { grace },
                clock = PinnedClock(noon),
            )()
            return currencyDao.balance.awardedSavedMsToday
        }

        val none = savedWith(emptyMap())
        val instaGrace = savedWith(mapOf("com.insta" to 5 * 60_000L))
        // Only Instagram's 5 min are spared; TikTok's 10 min still cost.
        assertEquals(5 * 60_000L, instaGrace - none)
    }

    @Test
    fun `mindful grace does not rescue a streak day that went over budget`() = runTest {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = daysAgoKey(1), consecutiveDetoxDays = 3)
        }
        val repo = StubScreenTimeRepo(
            savedMs = 0L,
            foregroundByDate = mapOf(daysAgoKey(1) to 90 * 60_000L), // over the 60 min budget
        )

        CalculateDetoxRewardsUseCase(
            repo, CurrencyRepository(currencyDao), blocked("com.instagram.android"),
            graceToday = { mapOf("com.instagram.android" to 60 * 60_000L) },
        )()

        assertEquals(0, currencyDao.balance.consecutiveDetoxDays)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:desktopTest --no-daemon --tests "com.questlog.domain.usecase.CalculateDetoxRewardsUseCaseTest"`
Expected: FAIL — `No parameter with name 'graceToday'`.

- [ ] **Step 3: Implement**

Constructor — insert before `clock`:

```kotlin
    private val isPremium: () -> Boolean = { false },
    /** Today's Mindful Unlock grace per package; added to that app's daily limit for the reward only. */
    private val graceToday: suspend () -> Map<String, Long> = { emptyMap() },
    private val clock: Clock = Clock.System,
```

In `invoke`, replace the fetch block:

```kotlin
        val blocked = blockedApps()
        val grace = graceToday()
        val savedMs = screenTimeRepo.fetchAndPersistToday(
            flaggedPackages = blocked.mapTo(mutableSetOf()) { it.packageName },
            startOfDayMs = startOfDay,
            allowances = blocked.associate { it.packageName to it.dailyLimitMs + (grace[it.packageName] ?: 0L) },
        )
```

`SharedModule.kt`, in the `CalculateDetoxRewardsUseCase(...)` factory add:

```kotlin
            graceToday = { get<MindfulUnlockRepository>().graceMsToday() },
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:desktopTest --no-daemon`
Expected: PASS — the whole shared suite (the streak test passes without the implementation too; it pins that grace never reaches the streak).

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "Mindful Unlocks: grace lowers the reward charge, never the streak"
```

---

### Task 5: `IntentJudge` client + proxy URL build config

**Files:**
- Modify: `app/build.gradle.kts` (defaultConfig + dependencies)
- Create: `app/src/main/java/com/example/questlog/unlock/IntentJudge.kt`
- Test: `app/src/test/java/com/example/questlog/unlock/IntentJudgeTest.kt`

**Interfaces:**
- Produces (package `com.example.questlog.unlock`):
  - `sealed interface Verdict { data class Judged(val purposeful: Double, val category: String) : Verdict; data object Unavailable : Verdict }`
  - `fun parseVerdict(json: String): Verdict`
  - `open class IntentJudge(baseUrl: String, installId: () -> String) { open suspend fun judge(appLabel: String, reason: String): Verdict }`
  - `object InstallId { fun get(prefs: SharedPreferences): String }`
  - `BuildConfig.UNLOCK_PROXY_URL: String`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.questlog.unlock

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class IntentJudgeTest {
    @Test fun `valid proxy answer parses`() = assertEquals(
        Verdict.Judged(0.91, "message"),
        parseVerdict("""{"purposeful":0.91,"category":"message"}"""),
    )

    @Test fun `junk answers are unavailable, never a grant`() {
        for (body in listOf(
            """{"purposeful":"high","category":"message"}""",
            """{"purposeful":0.9}""",
            """{"purposeful":0.9,"category":5}""",
            """{"purposeful":1.4,"category":"message"}""",
            "<html>502 Bad Gateway</html>",
            "",
        )) assertEquals(Verdict.Unavailable, parseVerdict(body), body)
    }

    @Test fun `placeholder proxy url never touches the network`() = runTest {
        val judge = IntentJudge("REPLACE_WITH_UNLOCK_PROXY_URL") { error("install id must not be read") }
        assertEquals(Verdict.Unavailable, judge.judge("Instagram", "reply to mum"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.unlock.IntentJudgeTest"`
Expected: FAIL — `parseVerdict` / `IntentJudge` unresolved.

- [ ] **Step 3: Implement**

`app/build.gradle.kts`, next to `val revenueCatKey`:

```kotlin
// Mindful Unlocks proxy (Cloudflare Worker). Placeholder = every judgment is "unavailable".
val unlockProxyUrl: String? = signingProp("UNLOCK_PROXY_URL", "unlockProxyUrl")
```

in `defaultConfig { ... }`:

```kotlin
        buildConfigField(
            "String",
            "UNLOCK_PROXY_URL",
            buildConfigStringLiteral(unlockProxyUrl ?: "REPLACE_WITH_UNLOCK_PROXY_URL"),
        )
```

in `dependencies { ... }` next to the other `implementation`s:

```kotlin
    implementation(libs.kotlinx.serialization.json)
```

`IntentJudge.kt`:

```kotlin
package com.example.questlog.unlock

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed interface Verdict {
    data class Judged(val purposeful: Double, val category: String) : Verdict
    /** Offline, timeout, non-200, bad JSON, or no proxy configured. Never grants grace. */
    data object Unavailable : Verdict
}

/** Parses the proxy's `{"purposeful": 0..1, "category": "..."}`; anything else is Unavailable. */
fun parseVerdict(json: String): Verdict = runCatching {
    val o = Json.parseToJsonElement(json).jsonObject
    val p = o.getValue("purposeful").jsonPrimitive
    val c = o.getValue("category").jsonPrimitive
    require(!p.isString && c.isString)
    val purposeful = p.double
    require(purposeful in 0.0..1.0)
    Verdict.Judged(purposeful, c.content)
}.getOrDefault(Verdict.Unavailable)

/** Calls the Mindful Unlocks proxy. `open` so ViewModel tests can script verdicts. */
open class IntentJudge(
    private val baseUrl: String,
    private val installId: () -> String,
) {
    open suspend fun judge(appLabel: String, reason: String): Verdict {
        if (!baseUrl.startsWith("https://")) return Verdict.Unavailable
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("${baseUrl.trimEnd('/')}/judge").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Install-Id", installId())
                    val body = buildJsonObject { put("app", appLabel); put("reason", reason) }.toString()
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    if (conn.responseCode != 200) Verdict.Unavailable
                    else parseVerdict(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(Verdict.Unavailable)
        }
    }

    private companion object { const val TIMEOUT_MS = 3_000 }
}

/** A random per-install id for the proxy's rate limit. Not tied to the user. */
object InstallId {
    private const val KEY = "install_id"
    fun get(prefs: SharedPreferences): String =
        prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.unlock.IntentJudgeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/example/questlog/unlock app/src/test/java/com/example/questlog/unlock
git commit -m "Mindful Unlocks: IntentJudge proxy client and UNLOCK_PROXY_URL"
```

---

### Task 6: `UnlockViewModel`

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/unlock/UnlockViewModel.kt`
- Test: `app/src/test/java/com/example/questlog/ui/unlock/UnlockViewModelTest.kt`

**Interfaces:**
- Consumes: `BlocklistRepository.current()`, `InstalledAppsProvider.launchableApps()`, `IntentJudge`, `Verdict` (Task 5), `MindfulUnlockRepository` (Task 3), `MindfulUnlockRule.FREE_UNLOCKS_PER_DAY` (Task 1).
- Produces (package `com.example.questlog.ui.unlock`):
  - `data class UnlockApp(val packageName: String, val label: String, val icon: Drawable?)`
  - `enum class UnlockPhase { Pick, Reason, Checking, Granted, Drifting, Unavailable }`
  - `data class UnlockUiState(apps: List<UnlockApp> = emptyList(), selected: UnlockApp? = null, reason: String = "", phase: UnlockPhase = UnlockPhase.Pick, isPremium: Boolean = false, usedToday: Int = 0)` with `val freeLeft: Int`
  - `sealed interface UnlockIntent { Select(app); SetReason(text); data object Check; data object Open; data object NotNow; data object Reset }`
  - `sealed interface UnlockEvent { data class Launch(val packageName: String); data object OpenPaywall; data object Close }`
  - `class UnlockViewModel(blocklistRepo, installedApps, judge, unlocks, isPremium: () -> Boolean)` with `uiState: StateFlow<UnlockUiState>`, `events: Flow<UnlockEvent>`, `onIntent(UnlockIntent)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.questlog.ui.unlock

import com.example.questlog.data.InstalledApp
import com.example.questlog.data.InstalledAppsProvider
import com.example.questlog.unlock.IntentJudge
import com.example.questlog.unlock.Verdict
import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.dao.MindfulUnlockDao
import com.questlog.data.local.dao.PackageGrace
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.MindfulUnlockRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

private class Blocked(vararg pkgs: String) : BlocklistDao {
    private val rows = pkgs.map { BlockedAppEntity(it, 0L) }
    override fun observeAll(): Flow<List<BlockedAppEntity>> = MutableStateFlow(rows)
    override suspend fun getAll() = rows
    override suspend fun get(packageName: String) = rows.find { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) = Unit
    override suspend fun delete(packageName: String) = Unit
}

private class Apps(vararg apps: Pair<String, String>) : InstalledAppsProvider {
    private val list = apps.map { InstalledApp(it.first, it.second, null) }
    override suspend fun launchableApps() = list
}

private class Unlocks : MindfulUnlockDao {
    val rows = mutableListOf<MindfulUnlockEntity>()
    override suspend fun insert(row: MindfulUnlockEntity): Long { rows += row; return rows.size.toLong() }
    override suspend fun graceByPackageForDate(date: String) =
        rows.filter { it.date == date }.groupBy { it.packageName }.map { (p, r) -> PackageGrace(p, r.sumOf { it.graceMs }) }
    override suspend fun countForDate(date: String) = rows.count { it.date == date }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class Judge(var verdict: Verdict) : IntentJudge("https://unused", { "id" }) {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun judge(appLabel: String, reason: String): Verdict {
            calls++
            gate?.await()
            return verdict
        }
    }

    private val insta = UnlockApp("com.insta", "Instagram", null)

    private fun TestScope.vm(
        judge: Judge,
        unlocks: Unlocks = Unlocks(),
        premium: () -> Boolean = { false },
    ): UnlockViewModel {
        val vm = UnlockViewModel(
            blocklistRepo = BlocklistRepository(Blocked("com.insta", "com.gone")),
            installedApps = Apps("com.insta" to "Instagram", "com.other" to "Other"),
            judge = judge,
            unlocks = MindfulUnlockRepository(unlocks),
            isPremium = premium,
        )
        advanceUntilIdle()
        return vm
    }

    private fun UnlockViewModel.ask(reason: String) {
        onIntent(UnlockIntent.Select(uiState.value.apps.single()))
        onIntent(UnlockIntent.SetReason(reason))
        onIntent(UnlockIntent.Check)
    }

    @Test
    fun `lists only blocked apps that are installed`() = runTest {
        assertEquals(listOf("Instagram"), vm(Judge(Verdict.Unavailable)).uiState.value.apps.map { it.label })
    }

    @Test
    fun `a purposeful answer grants grace and records it`() = runTest {
        val unlocks = Unlocks()
        val vm = vm(Judge(Verdict.Judged(0.9, "message")), unlocks)
        vm.ask("reply to mum")
        advanceUntilIdle()
        assertEquals(UnlockPhase.Granted, vm.uiState.value.phase)
        assertEquals(listOf(5 * 60_000L), unlocks.rows.map { it.graceMs })
        vm.onIntent(UnlockIntent.Open)
        assertEquals(UnlockEvent.Launch("com.insta"), vm.events.first())
    }

    @Test
    fun `a drifting answer is recorded with no grace`() = runTest {
        val unlocks = Unlocks()
        val vm = vm(Judge(Verdict.Judged(0.95, "boredom")), unlocks)
        vm.ask("bored")
        advanceUntilIdle()
        assertEquals(UnlockPhase.Drifting, vm.uiState.value.phase)
        assertEquals(listOf(0L), unlocks.rows.map { it.graceMs })
    }

    @Test
    fun `unavailable records nothing and does not use the free unlock`() = runTest {
        val unlocks = Unlocks()
        val vm = vm(Judge(Verdict.Unavailable), unlocks)
        vm.ask("reply to mum")
        advanceUntilIdle()
        assertEquals(UnlockPhase.Unavailable, vm.uiState.value.phase)
        assertEquals(0, unlocks.rows.size)
        assertEquals(1, vm.uiState.value.freeLeft)
    }

    @Test
    fun `free cap opens the paywall without calling the proxy`() = runTest {
        val judge = Judge(Verdict.Judged(0.9, "message"))
        val vm = vm(judge)
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Reset)
        vm.ask("check a post"); advanceUntilIdle()
        assertEquals(1, judge.calls)
        assertEquals(UnlockEvent.OpenPaywall, vm.events.first())
    }

    @Test
    fun `pro bought after the cap is honoured on the next check`() = runTest {
        var pro = false
        val judge = Judge(Verdict.Judged(0.9, "message"))
        val vm = vm(judge, premium = { pro })
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Reset)
        pro = true
        vm.ask("check a post"); advanceUntilIdle()
        assertEquals(2, judge.calls)
        assertEquals(UnlockPhase.Granted, vm.uiState.value.phase)
    }

    @Test
    fun `a second check while one is in flight is ignored`() = runTest {
        val unlocks = Unlocks()
        val judge = Judge(Verdict.Judged(0.9, "message")).apply { gate = CompletableDeferred() }
        val vm = vm(judge, unlocks, premium = { true })
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Check); advanceUntilIdle()
        judge.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, judge.calls)
        assertEquals(1, unlocks.rows.size)
    }

    @Test
    fun `blank reason does not check`() = runTest {
        val judge = Judge(Verdict.Judged(0.9, "message"))
        val vm = vm(judge)
        vm.ask("   "); advanceUntilIdle()
        assertEquals(0, judge.calls)
        assertEquals(UnlockPhase.Reason, vm.uiState.value.phase)
    }

    @Test
    fun `reset starts fresh at the app list`() = runTest {
        val vm = vm(Judge(Verdict.Judged(0.9, "message")))
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Reset); advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(UnlockPhase.Pick, s.phase)
        assertEquals(null, s.selected)
        assertEquals("", s.reason)
        assertEquals(1, s.usedToday)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.unlock.UnlockViewModelTest"`
Expected: FAIL — `UnlockViewModel` and friends unresolved.

- [ ] **Step 3: Implement `UnlockViewModel.kt`**

```kotlin
package com.example.questlog.ui.unlock

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.data.InstalledAppsProvider
import com.example.questlog.unlock.IntentJudge
import com.example.questlog.unlock.Verdict
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.MindfulUnlockRepository
import com.questlog.domain.unlock.MindfulUnlockRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockApp(val packageName: String, val label: String, val icon: Drawable?)

enum class UnlockPhase { Pick, Reason, Checking, Granted, Drifting, Unavailable }

data class UnlockUiState(
    val apps: List<UnlockApp> = emptyList(),
    val selected: UnlockApp? = null,
    val reason: String = "",
    val phase: UnlockPhase = UnlockPhase.Pick,
    val isPremium: Boolean = false,
    val usedToday: Int = 0,
) {
    val freeLeft: Int get() = (MindfulUnlockRule.FREE_UNLOCKS_PER_DAY - usedToday).coerceAtLeast(0)
}

sealed interface UnlockIntent {
    data class Select(val app: UnlockApp) : UnlockIntent
    data class SetReason(val text: String) : UnlockIntent
    data object Check : UnlockIntent
    data object Open : UnlockIntent
    data object NotNow : UnlockIntent
    /** Fired on screen entry: back to the app list, fresh quota. */
    data object Reset : UnlockIntent
}

sealed interface UnlockEvent {
    data class Launch(val packageName: String) : UnlockEvent
    data object OpenPaywall : UnlockEvent
    data object Close : UnlockEvent
}

class UnlockViewModel(
    private val blocklistRepo: BlocklistRepository,
    private val installedApps: InstalledAppsProvider,
    private val judge: IntentJudge,
    private val unlocks: MindfulUnlockRepository,
    private val isPremium: () -> Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val _events = Channel<UnlockEvent>(Channel.BUFFERED)
    val events: Flow<UnlockEvent> = _events.receiveAsFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val blocked = blocklistRepo.current().mapTo(mutableSetOf()) { it.packageName }
        val apps = installedApps.launchableApps()
            .filter { it.packageName in blocked }
            .map { UnlockApp(it.packageName, it.label, it.icon) }
        _uiState.update { it.copy(apps = apps, isPremium = isPremium(), usedToday = unlocks.countToday()) }
    }

    fun onIntent(intent: UnlockIntent) {
        when (intent) {
            is UnlockIntent.Select -> _uiState.update { it.copy(selected = intent.app, reason = "", phase = UnlockPhase.Reason) }
            is UnlockIntent.SetReason -> _uiState.update { it.copy(reason = intent.text.take(MAX_REASON)) }
            UnlockIntent.Check -> check()
            UnlockIntent.Open -> _uiState.value.selected?.let { _events.trySend(UnlockEvent.Launch(it.packageName)) }
            UnlockIntent.NotNow -> _events.trySend(UnlockEvent.Close)
            UnlockIntent.Reset -> {
                _uiState.update { it.copy(selected = null, reason = "", phase = UnlockPhase.Pick) }
                load()
            }
        }
    }

    private fun check() {
        val s = _uiState.value
        val app = s.selected ?: return
        val reason = s.reason.trim()
        if (reason.isEmpty() || s.phase == UnlockPhase.Checking) return
        _uiState.update { it.copy(phase = UnlockPhase.Checking) } // synchronous: blocks a double tap
        viewModelScope.launch {
            val premium = isPremium() // read live: a purchase since the screen opened counts
            if (!premium && unlocks.countToday() >= MindfulUnlockRule.FREE_UNLOCKS_PER_DAY) {
                _uiState.update { it.copy(phase = UnlockPhase.Reason, isPremium = false) }
                _events.send(UnlockEvent.OpenPaywall)
                return@launch
            }
            val phase = when (val v = judge.judge(app.label, reason)) {
                is Verdict.Judged ->
                    if (unlocks.record(app.packageName, v.category, v.purposeful) > 0) UnlockPhase.Granted
                    else UnlockPhase.Drifting
                Verdict.Unavailable -> UnlockPhase.Unavailable
            }
            _uiState.update { it.copy(phase = phase, isPremium = premium, usedToday = unlocks.countToday()) }
        }
    }

    private companion object { const val MAX_REASON = 200 }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.unlock.UnlockViewModelTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/unlock/UnlockViewModel.kt app/src/test/java/com/example/questlog/ui/unlock
git commit -m "Mindful Unlocks: UnlockViewModel (judge, record, free cap)"
```

---

### Task 7: Unlock screen, Today row, paywall perk, wiring

**Files:**
- Create: `app/src/main/java/com/example/questlog/ui/unlock/UnlockScreen.kt`
- Create: `app/src/main/java/com/example/questlog/ui/today/MindfulUnlockRow.kt`
- Modify: `app/src/main/java/com/example/questlog/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt`
- Modify: `app/src/main/java/com/example/questlog/ui/paywall/PaywallScreen.kt`
- Modify: `app/src/main/java/com/example/questlog/QuestLogApp.kt`

**Interfaces:**
- Consumes: everything from Task 6; `DashboardIntent.OpenPaywall`; `MilestoneOfferStore.PREFS`; `InstallId`, `IntentJudge`, `BuildConfig.UNLOCK_PROXY_URL` (Task 5).
- Produces: `@Composable fun UnlockScreen(state: UnlockUiState, onIntent: (UnlockIntent) -> Unit, onBack: () -> Unit)`; `@Composable fun MindfulUnlockRow(onOpen: () -> Unit, modifier: Modifier = Modifier)`; `TodayScreen(..., onOpenUnlock: () -> Unit)`.

This task is UI wiring; its gate is compilation plus the whole suite (the behaviour is tested in Task 6).

- [ ] **Step 1: `MindfulUnlockRow.kt`** (styled like `RealmStrip`)

```kotlin
package com.example.questlog.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType

@Composable
fun MindfulUnlockRow(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = QuestSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Open an app mindfully", style = QuestType.bodyLarge, color = c.inkPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(QuestSpacing.xs))
        Icon(QuestIcons.ArrowRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
    }
}
```

- [ ] **Step 2: `TodayScreen.kt`** — add param `onOpenUnlock: () -> Unit` after `onOpenBlocklist`; after `QuestLedger(state.dailyQuests)` and its following `Hairline()` insert:

```kotlin
            MindfulUnlockRow(onOpen = onOpenUnlock)
            Hairline()
```

and add `onOpenUnlock = {}` to both preview calls.

- [ ] **Step 3: `UnlockScreen.kt`**

```kotlin
package com.example.questlog.ui.unlock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold

@Composable
fun UnlockScreen(state: UnlockUiState, onIntent: (UnlockIntent) -> Unit, onBack: () -> Unit) {
    val c = QuestLogTheme.colors
    QuestScaffold(
        header = {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary) }
                Text("Open mindfully", style = QuestType.screenTitle, color = c.inkPrimary)
            }
        },
    ) {
        Hairline()
        val app = state.selected
        if (app == null || state.phase == UnlockPhase.Pick) {
            LazyColumn {
                items(state.apps, key = { it.packageName }) { a ->
                    Text(
                        a.label,
                        style = QuestType.bodyLarge,
                        color = c.inkPrimary,
                        modifier = Modifier.fillMaxWidth().clickable { onIntent(UnlockIntent.Select(a)) }
                            .padding(vertical = QuestSpacing.md),
                    )
                    Hairline()
                }
            }
            return@QuestScaffold
        }
        Column(Modifier.padding(top = QuestSpacing.lg), verticalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
            when (state.phase) {
                UnlockPhase.Reason, UnlockPhase.Checking -> {
                    Text("What are you opening ${app.label} for?", style = QuestType.heroLine, color = c.inkPrimary)
                    OutlinedTextField(
                        value = state.reason,
                        onValueChange = { onIntent(UnlockIntent.SetReason(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.phase == UnlockPhase.Reason,
                    )
                    PrimaryButton(
                        if (state.phase == UnlockPhase.Checking) "Checking…" else "Check",
                        enabled = state.phase == UnlockPhase.Reason && state.reason.isNotBlank(),
                    ) { onIntent(UnlockIntent.Check) }
                    Caption(if (state.isPremium) "Unlimited with Pro" else if (state.freeLeft > 0) "1 free mindful unlock left today" else "Unlimited with Pro")
                    Caption("Your answer is checked by AI and not saved.")
                }
                UnlockPhase.Granted -> {
                    Text("5 minutes, no charge.", style = QuestType.heroLine, color = c.inkPrimary)
                    PrimaryButton("Open ${app.label}") { onIntent(UnlockIntent.Open) }
                }
                UnlockPhase.Drifting -> {
                    Text("That sounds like drifting — it'll count today.", style = QuestType.heroLine, color = c.inkPrimary)
                    PrimaryButton("Open anyway") { onIntent(UnlockIntent.Open) }
                    TextButton(onClick = { onIntent(UnlockIntent.NotNow) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Not now".uppercase(), style = QuestType.caption, color = c.inkMuted)
                    }
                }
                UnlockPhase.Unavailable -> {
                    Text("Couldn't check right now.", style = QuestType.heroLine, color = c.inkPrimary)
                    PrimaryButton("Open anyway") { onIntent(UnlockIntent.Open) }
                }
                UnlockPhase.Pick -> Unit
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = QuestLogTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = QuestShapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = c.earned, contentColor = c.ground),
    ) { Text(label, style = QuestType.bodyLarge) }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = QuestType.caption, color = QuestLogTheme.colors.inkMuted)
}
```

(If `QuestScaffold`'s content lambda is not a `ColumnScope`/receiver that allows `return@QuestScaffold`, replace the early return with an `else` branch around the `Column`.)

- [ ] **Step 4: `QuestLogRoot.kt`**

- `private enum class Screen { Today, Realm, Blocklist, Unlock }`
- Add `import androidx.compose.runtime.rememberCoroutineScope`, `import kotlinx.coroutines.launch`, `import com.example.questlog.ui.unlock.UnlockEvent`, `import com.example.questlog.ui.unlock.UnlockIntent`, `import com.example.questlog.ui.unlock.UnlockScreen`, `import com.example.questlog.ui.unlock.UnlockViewModel`.
- In `TodayScreen(...)` call add `onOpenUnlock = { screen = Screen.Unlock },`.
- Add the branch after `Screen.Blocklist -> { ... }`:

```kotlin
                    Screen.Unlock -> {
                        val unlockVm = koinViewModel<UnlockViewModel>()
                        val unlockState by unlockVm.uiState.collectAsState()
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(Unit) { unlockVm.onIntent(UnlockIntent.Reset) }
                        LaunchedEffect(unlockVm) {
                            unlockVm.events.collect { e ->
                                when (e) {
                                    is UnlockEvent.Launch -> {
                                        val launch = context.packageManager.getLaunchIntentForPackage(e.packageName)
                                        if (launch != null) {
                                            context.startActivity(launch)
                                            screen = Screen.Today
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("Can't open that app.") }
                                        }
                                    }
                                    UnlockEvent.OpenPaywall -> viewModel.onIntent(DashboardIntent.OpenPaywall)
                                    UnlockEvent.Close -> screen = Screen.Today
                                }
                            }
                        }
                        UnlockScreen(
                            state = unlockState,
                            onIntent = unlockVm::onIntent,
                            onBack = { screen = Screen.Today },
                        )
                    }
```

- [ ] **Step 5: Paywall perk** — in `PaywallScreen.kt` add to `PRO_PERKS`:

```kotlin
    Perk("✦", "Mindful Unlocks", "Say why, get 5 minutes free — as often as you like"),
```

- [ ] **Step 6: Koin (`QuestLogApp.kt`)** — imports `com.example.questlog.unlock.IntentJudge`, `com.example.questlog.unlock.InstallId`, `com.example.questlog.ui.unlock.UnlockViewModel`; in `appModule`:

```kotlin
    single {
        val prefs = androidContext().getSharedPreferences(MilestoneOfferStore.PREFS, Context.MODE_PRIVATE)
        IntentJudge(BuildConfig.UNLOCK_PROXY_URL) { InstallId.get(prefs) }
    }
    viewModel {
        UnlockViewModel(
            blocklistRepo = get(),
            installedApps = get(),
            judge = get(),
            unlocks = get(),
            isPremium = { get<BillingManager>().isPremium.value },
        )
    }
```

- [ ] **Step 7: Compile and run the suites**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`
Expected: PASS and BUILD SUCCESSFUL. Also `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon` (instrumented tests call `TodayScreen`? if so add `onOpenUnlock = {}` there) → BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "Mindful Unlocks: Unlock screen, Today entry row, Pro perk, wiring"
```

---

### Task 8: Proxy Worker

**Files:**
- Create: `proxy/src/index.js`, `proxy/wrangler.toml`, `proxy/package.json`, `proxy/package-lock.json` (generated), `proxy/test/index.test.js`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `POST /judge` — body `{app, reason}`, header `X-Install-Id`; responses `200 {purposeful, category}`, `400`, `404`, `429`, `502`. Consumed by `IntentJudge` (Task 5).

- [ ] **Step 1: Scaffold**

`proxy/package.json`:

```json
{
  "name": "questlog-unlock-proxy",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest run",
    "deploy": "npx wrangler deploy"
  }
}
```

Run: `cd proxy && npm install --save-dev vitest` (pins the current version into `package.json` and writes `package-lock.json`).

`.gitignore`: add `proxy/node_modules/` and `proxy/.wrangler/`.

`proxy/wrangler.toml`:

```toml
name = "questlog-unlock"
main = "src/index.js"
compatibility_date = "2026-09-01"

# 10 judgments / minute / install id, per Cloudflare location.
# Ceiling: the id is client-chosen — this stops accidents, not attackers.
[[ratelimits]]
name = "LIMITER"
namespace_id = "1001"

  [ratelimits.simple]
  limit = 10
  period = 60
```

- [ ] **Step 2: Write the failing test** — `proxy/test/index.test.js`

```js
import { afterEach, describe, expect, it, vi } from 'vitest'
import worker from '../src/index.js'

const env = { TYPESAFE_API_KEY: 'k', LIMITER: { limit: async () => ({ success: true }) } }

const judge = (body, headers = { 'X-Install-Id': 'abc' }) =>
  worker.fetch(new Request('https://x/judge', { method: 'POST', headers, body: JSON.stringify(body) }), env)

const typesafe = (answers, status = 200) =>
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ answers }), { status })))

afterEach(() => vi.unstubAllGlobals())

describe('POST /judge', () => {
  it('returns purposeful and category', async () => {
    typesafe({ purposeful: { type: 'noul', noul: 0.91 }, category: { type: 'choice', choice: 'message' } })
    const res = await judge({ app: 'Instagram', reason: 'reply to mum' })
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ purposeful: 0.91, category: 'message' })
    const sent = JSON.parse(fetch.mock.calls[0][1].body)
    expect(sent.state).toEqual({ app: 'Instagram', reason: 'reply to mum' })
    expect(Object.keys(sent.questions).sort()).toEqual(['category', 'purposeful'])
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer k')
  })

  it('rejects bad input without calling TypeSafe', async () => {
    typesafe({})
    for (const [body, headers] of [
      [{ app: 'Instagram', reason: '  ' }, undefined],
      [{ app: 'Instagram', reason: 'x'.repeat(201) }, undefined],
      [{ app: '', reason: 'hi' }, undefined],
      [{ app: 'Instagram', reason: 'hi' }, {}],
    ]) expect((await judge(body, headers)).status).toBe(400)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('maps TypeSafe errors and malformed answers to 502', async () => {
    typesafe({}, 529)
    expect((await judge({ app: 'Instagram', reason: 'hi' })).status).toBe(502)
    typesafe({ purposeful: { noul: 'high' } })
    expect((await judge({ app: 'Instagram', reason: 'hi' })).status).toBe(502)
  })

  it('rate-limits per install id', async () => {
    typesafe({})
    const limited = { ...env, LIMITER: { limit: async () => ({ success: false }) } }
    const res = await worker.fetch(
      new Request('https://x/judge', { method: 'POST', headers: { 'X-Install-Id': 'abc' }, body: '{"app":"a","reason":"b"}' }),
      limited,
    )
    expect(res.status).toBe(429)
  })

  it('404s anything else', async () => {
    const res = await worker.fetch(new Request('https://x/', { method: 'GET' }), env)
    expect(res.status).toBe(404)
  })
})
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd proxy && npm test`
Expected: FAIL — cannot resolve `../src/index.js`.

- [ ] **Step 4: Implement `proxy/src/index.js`**

```js
// Mindful Unlocks proxy: holds the TypeSafe key, fixes the questions, returns two numbers.
// It never logs request bodies — the player's reason is not stored anywhere.

const TYPESAFE_URL = 'https://api.typesafe.ai/v1/systemone'

const QUESTIONS = {
  purposeful: {
    type: 'noul',
    instructions:
      'Does `reason` describe a specific, bounded task to do in `app` — such as replying to a particular person, ' +
      'posting something specific, or looking up one thing — rather than open-ended browsing or passing time?',
    criteria: {
      true: 'A concrete task with a natural end point',
      false: 'Browsing, scrolling, boredom, habit, or no clear task',
    },
  },
  category: {
    type: 'choice',
    instructions: 'What is the main reason for opening `app`?',
    criteria: {
      message: 'Replying to or contacting a specific person or group',
      create: 'Posting or sharing something specific',
      lookup: 'Finding one specific piece of information',
      work: 'A job, school, or business task',
      boredom: 'Passing time or nothing better to do',
      habit: 'Opening it automatically, out of habit',
      unclear: 'Too vague or unrelated to tell',
    },
  },
}

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const text = (v) => (typeof v === 'string' ? v.trim() : '')

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url)
    if (request.method !== 'POST' || pathname !== '/judge') return json({ error: 'not found' }, 404)

    const installId = text(request.headers.get('X-Install-Id'))
    const body = await request.json().catch(() => null)
    const app = text(body?.app)
    const reason = text(body?.reason)
    if (!installId || app.length < 1 || app.length > 60 || reason.length < 1 || reason.length > 200) {
      return json({ error: 'invalid input' }, 400)
    }

    const { success } = await env.LIMITER.limit({ key: installId })
    if (!success) return json({ error: 'rate limited' }, 429)

    let res
    try {
      res = await fetch(TYPESAFE_URL, {
        method: 'POST',
        headers: { Authorization: `Bearer ${env.TYPESAFE_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ state: { app, reason }, model: 'jev-latest', questions: QUESTIONS }),
      })
    } catch {
      return json({ error: 'upstream' }, 502)
    }
    if (!res.ok) return json({ error: 'upstream' }, 502)

    const data = await res.json().catch(() => null)
    const purposeful = data?.answers?.purposeful?.noul
    const category = data?.answers?.category?.choice
    if (typeof purposeful !== 'number' || typeof category !== 'string') return json({ error: 'upstream' }, 502)
    return json({ purposeful, category })
  },
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd proxy && npm test`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add proxy/src proxy/test proxy/wrangler.toml proxy/package.json proxy/package-lock.json .gitignore
git commit -m "Mindful Unlocks: Cloudflare Worker proxy for TypeSafe"
```

---

### Task 9: Docs, CI env, full check

**Files:**
- Modify: `.github/workflows/deploy-internal.yml` (the step whose `env:` has `REVENUECAT_API_KEY`)
- Modify: `README.md`, `CLAUDE.md`

- [ ] **Step 1: CI** — under the existing `REVENUECAT_API_KEY: ${{ secrets.REVENUECAT_API_KEY }}` line add:

```yaml
          UNLOCK_PROXY_URL: ${{ secrets.UNLOCK_PROXY_URL }}
```

- [ ] **Step 2: README** — in the screens paragraph mention **Unlock** ("Open an app mindfully", reached from Today); in *Persistence* bump to schema **v10** and add the row `| mindful_unlock | id | date, packageName, category, purposeful, graceMs, createdAt — one row per judged Mindful Unlock; no reason text |`; under *Configuration* add:

```markdown
- **Mindful Unlocks**: the Unlock screen sends the player's typed reason to the Cloudflare
  Worker in `proxy/` (TypeSafe key as the `TYPESAFE_API_KEY` Worker secret; `npm test`,
  `npx wrangler deploy`). A purposeful answer (`MindfulUnlockRule`: Noul ≥ 0.7, not
  boredom/habit/unclear) grants 5 min of grace, added to that app's allowance for today's
  reward only (never the streak). Free: 1 judged unlock/day; Pro unlimited. The app reads the
  Worker URL from `UNLOCK_PROXY_URL` (env / `keystore.properties` `unlockProxyUrl`); the
  placeholder makes every check "Couldn't check right now".
```

and in the secrets table add `| UNLOCK_PROXY_URL | Mindful Unlocks Worker URL |`.

- [ ] **Step 3: CLAUDE.md** — under *Testing patterns*, after the fake-DAO bullet append: `` `MindfulUnlockDao` has fakes in `MindfulUnlockRepositoryTest` (shared) and `UnlockViewModelTest` (app). `` Under *Invariants / gotchas* add:

```markdown
- Mindful Unlock grace is reward-only: `CalculateDetoxRewardsUseCase` adds
  `graceToday()` to each app's allowance; the streak reads raw totals and never sees it.
  The typed reason is never persisted. `proxy/` is a Cloudflare Worker, not in Android CI
  (`cd proxy && npm test`).
```

- [ ] **Step 4: Spec sync** — in `docs/superpowers/specs/2026-09-27-mindful-unlocks-design.md` section 3, drop
  `observeCountForDate(date): Flow<Int>` and `observeCountToday(): Flow<Int>` (the ViewModel re-reads
  `countToday()` after each check and on `Reset`, so no flow is needed).

- [ ] **Step 5: Full check**

Run: `./gradlew :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug --no-daemon` and `cd proxy && npm test`
Expected: BUILD SUCCESSFUL; proxy 5 passed.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/deploy-internal.yml README.md CLAUDE.md docs/superpowers/specs/2026-09-27-mindful-unlocks-design.md
git commit -m "Docs: Mindful Unlocks, proxy, UNLOCK_PROXY_URL"
```

---

## Manual verification (not CI)

1. `cd proxy && npx wrangler secret put TYPESAFE_API_KEY && npx wrangler deploy`; put the URL in `keystore.properties` `unlockProxyUrl`.
2. `curl -s -X POST <url>/judge -H 'X-Install-Id: t' -d '{"app":"Instagram","reason":"reply to mum about dinner"}'` → high `purposeful`, `message`. Try "just bored" → low / `boredom`.
3. Debug build: Today → "Open an app mindfully" → Instagram → "reply to mum" → "5 minutes, no charge." → Open → Instagram launches. Second attempt the same day (free) → paywall.
