# Design: Make the Pro perks real

## Context

The paywall (`PaywallModal`) advertises four Pro perks:

| Perk | Status |
|---|---|
| Crystal Castle — exclusive building | **Real** — `PurchaseBuildingUseCase` returns `PremiumRequired` for `tile.isPremium` unless `isPremiumUser` |
| Aurora Fountain — exclusive building | **Real** — same path |
| 2× XP Multiplier — "Double all focus time rewards" | **Not implemented** |
| Streak Freeze Shield — "Protect your streak if you miss a day" | **Not implemented** |

Both missing perks belong to reward / streak logic that lives in
`CalculateDetoxRewardsUseCase` (the `shared` module). The premium signal
(`BillingManager.isPremium: StateFlow<Boolean>`) lives in the `app` module and is
currently only read by `DashboardViewModel`. Premium can be toggled for the demo via
`BillingManager.setDebugPremium(true)` (the "Unlock Pro" button).

## Goals

- A premium user's detox-time reward (XP **and** gold) is doubled, on top of the streak
  multiplier.
- A premium user's streak survives one missed (over-budget) day per 7 days.
- Both effects are visible on the dashboard.
- Non-premium behaviour is unchanged.

## Non-goals

- Real RevenueCat purchasing — the demo `setDebugPremium` path stays as the only way to
  become premium.
- Changing `PurchaseBuildingUseCase` — it already gates premium buildings correctly.
- Changing paywall copy — it already describes these perks accurately.
- Doubling the flat daily-quest rewards (`EvaluateDailyQuestsUseCase` stays flat).

## Approach

Chosen: **inject an `isPremium: () -> Boolean` lambda into the shared use case; persist
the freeze state as one new `currency_balance` column.** This matches the existing
codebase patterns — `CalculateDetoxRewardsUseCase` already takes an
`evaluateDailyQuests: suspend () -> Unit` lambda, and columns have been added to
`currency_balance` and dropped from it via one-line `ALTER TABLE` migrations several times
(`rewardDate`, `awardedSavedMsToday`, `currentLevel` removed, `savedMs` removed).

Rejected: a full `PremiumStatusProvider` OO abstraction threaded through every use case
(ceremony for one boolean); persisting the entitlement itself into Room (adds a
`BillingManager → DB` sync path; RevenueCat's SDK already caches entitlements offline).

## Components

### 1. `PremiumStatusProvider` (new, `shared/src/commonMain/.../domain/`)

```kotlin
package com.questlog.domain

/** Bridges the app-module billing state into shared use cases. */
fun interface PremiumStatusProvider {
    fun isPremium(): Boolean
}
```

A `fun interface` (not a bare `() -> Boolean`) so the Koin lookup is by a distinct type,
not "any zero-arg boolean lambda".

### 2. DI wiring

**`appModule`** (`app/src/main/java/.../QuestLogApp.kt`):

```kotlin
single<PremiumStatusProvider> {
    PremiumStatusProvider { get<BillingManager>().isPremium.value }
}
```

**`sharedModule`** (`shared/src/commonMain/.../di/SharedModule.kt`) — the existing
`CalculateDetoxRewardsUseCase` factory gains one argument:

```kotlin
factory {
    val quests = get<EvaluateDailyQuestsUseCase>()
    CalculateDetoxRewardsUseCase(
        screenTimeRepo = get(),
        currencyRepo = get(),
        flaggedPackages = defaultFlaggedPackages,
        evaluateDailyQuests = { quests() },
        isPremium = { getOrNull<PremiumStatusProvider>()?.isPremium() ?: false },
    )
}
```

`getOrNull` → tests that load only `sharedModule` (or construct the use case directly)
get the `{ false }` default without needing a `PremiumStatusProvider`.

### 3. `CalculateDetoxRewardsUseCase`

Constructor gains `private val isPremium: () -> Boolean = { false }`.

Add a constant: `private const val PREMIUM_MULTIPLIER = 2f` (or a constructor param with
that default, matching `dailyFlaggedBudgetMs`).

**2× multiplier** — in `invoke()`, replace:

```kotlin
val multiplier = TimeConversion.streakMultiplier(streak)
```

with:

```kotlin
val premiumMultiplier = if (isPremium()) PREMIUM_MULTIPLIER else 1f
val multiplier = TimeConversion.streakMultiplier(streak) * premiumMultiplier
```

`multiplier` already feeds both `TimeConversion.xpEarned(...)` and
`TimeConversion.goldEarned(...)`, so XP and gold both double. `DetoxMetrics.streakMultiplier`
continues to report `TimeConversion.streakMultiplier(streak)` (streak-only) — its name and
the existing tests expect that, and nothing on the dashboard reads it.

**Streak freeze** — `evaluateStreak` gains the `balance` row as a parameter:

```kotlin
private suspend fun evaluateStreak(
    lastDayKey: String,
    today: LocalDate,
    currentStreak: Int,
    balance: CurrencyBalance?,
): Int {
    val lastDay = runCatching { LocalDate.parse(lastDayKey) }.getOrNull() ?: return currentStreak
    val gapDays = lastDay.daysUntil(today)
    if (gapDays <= 0) return currentStreak
    val phoneFreeGapDays = gapDays - 1

    if (screenTimeRepo.totalForegroundMs(lastDayKey) <= dailyFlaggedBudgetMs) {
        return currentStreak + gapDays
    }
    // lastDay was over budget — the streak would reset.
    if (isPremium() && StreakFreeze.isRechargedOn(balance?.streakFreezeLastUsed.orEmpty(), today)) {
        currencyRepo.setStreakFreezeUsed(today.toString())
        return currentStreak + phoneFreeGapDays
    }
    return phoneFreeGapDays
}
```

`StreakFreeze.isRechargedOn` (see §5) is the shared 7-day rule. The call site in
`invoke()` passes the already-fetched `balance`.

### 4. Persistence — schema v7

**`CurrencyBalance`** (`shared/.../data/local/entity/`) gains:

```kotlin
/** ISO date the streak-freeze charge was last spent; "" if never / recharged. */
val streakFreezeLastUsed: String = "",
```

**`CurrencyDao`**:

```kotlin
@Query("UPDATE currency_balance SET streakFreezeLastUsed = :date, updatedAt = :now WHERE id = 1")
suspend fun setStreakFreezeUsed(date: String, now: Long)
```

**`CurrencyRepository`**:

```kotlin
suspend fun setStreakFreezeUsed(date: String) {
    ensureInitialized()
    dao.setStreakFreezeUsed(date, Clock.System.now().toEpochMilliseconds())
}
```

**`QuestLogDatabase`** — `version = 7`.

**`QuestLogMigrations`**:

```kotlin
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `currency_balance` ADD COLUMN `streakFreezeLastUsed` TEXT NOT NULL DEFAULT ''"
        )
    }
}
// appended to questLogMigrations
```

Exported schema `7.json` regenerates on build.

### 5. Making it visible

**`com.questlog.util.StreakFreeze`** (new, pure) — the single home for the recharge rule:

```kotlin
object StreakFreeze {
    const val COOLDOWN_DAYS = 7
    /** True if a freeze charge is available on [today] given the last-used date ("" = never). */
    fun isRechargedOn(lastUsedKey: String, today: LocalDate): Boolean {
        if (lastUsedKey.isEmpty()) return true
        val lastUsed = runCatching { LocalDate.parse(lastUsedKey) }.getOrNull() ?: return true
        return lastUsed.daysUntil(today) >= COOLDOWN_DAYS
    }
}
```

Called from both `CalculateDetoxRewardsUseCase` (§3) and `CurrencyRepository` (below).

**`PlayerStats`** (`shared/.../domain/model/`) gains `val streakFreezeReady: Boolean`.

**`CurrencyRepository.observePlayerStats()`** computes it in the `.map`:

```kotlin
val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
// …
streakFreezeReady = StreakFreeze.isRechargedOn(b.streakFreezeLastUsed, today),
```

**`StatsCard`** (`app/.../ui/components/`) — signature gains `isPremium: Boolean`; the
call in `DashboardScreen` passes `uiState.isPremium`. When `isPremium`, render two small
chips next to the streak badge:

- **"⚡ 2× XP"** — always shown while premium (gold/arcane).
- **"🛡️ SHIELD"** when `stats.streakFreezeReady`, **"🛡️ RECHARGING"** otherwise (emerald / muted).

`DashboardUiState.stats` already carries `PlayerStats`; `DashboardUiState.isPremium`
already exists. No ViewModel logic change beyond passing the flag down.

## Data flow

```
BillingManager.isPremium (app)
        │  single<PremiumStatusProvider> { { .value } }
        ▼
CalculateDetoxRewardsUseCase.isPremium()   ── on every detox tick ──►
        ├─ multiplier = streakMult × 2      → currencyRepo.addRewards (doubled)
        └─ evaluateStreak: over-budget day + freeze available
                                            → currencyRepo.setStreakFreezeUsed(today)
                                            → streak preserved
        ▼
currency_balance row (xp, gold, consecutiveDetoxDays, streakFreezeLastUsed)
        ▼
CurrencyRepository.observePlayerStats()  → PlayerStats(streakFreezeReady = …)
        ▼
GetDashboardStatsUseCase → DashboardState.stats → DashboardViewModel.uiState
        ▼
StatsCard(stats, isPremium = uiState.isPremium)  → "⚡ 2× XP" / "🛡️ SHIELD" chips
```

## Error handling / edge cases

- **Premium lookup fails / provider absent** → `getOrNull` returns null → `isPremium()`
  is `false` → non-premium behaviour. No crash.
- **Corrupt `streakFreezeLastUsed`** (`LocalDate.parse` throws) → treated as "recharged"
  (`freezeAvailable` returns true) — fail-open, favours the user.
- **Premium turns off between ticks** → next tick applies the 1× multiplier; already-granted
  doubled rewards are kept (consistent with the existing high-water-mark "never claw back").
- **Freeze + long gap** — `evaluateStreak` only ever evaluates one `lastDay`; the
  `phoneFreeGapDays` between are assumed clean. So at most one missed day per rollover,
  and the freeze protects exactly that one.
- **Freeze consumed but streak was already 0** → `setStreakFreezeUsed` still fires and
  `currentStreak + phoneFreeGapDays` is returned; harmless (spends a charge to preserve 0).
  Acceptable — the reset branch is only reached on a genuine over-budget day.

## Testing (TDD)

### `CalculateDetoxRewardsUseCaseTest` (new cases)
- `premium doubles the detox XP and gold` — `isPremium = { true }`, fixed `savedMs` →
  XP and gold are exactly 2× the non-premium result.
- `premium 2x stacks on top of the streak multiplier` — streak = 10 (2.0×) + premium →
  effective 4.0× on the base rate.
- `streak freeze preserves a premium user's streak on a missed day` — over-budget
  `lastDay`, premium, `streakFreezeLastUsed = ""` → streak unchanged (or + clean gap
  days), and `currencyDao.balance.streakFreezeLastUsed == today`.
- `streak freeze on cooldown does not save the streak` — `streakFreezeLastUsed` = 3 days
  ago → streak resets.
- `a non-premium user's streak is not frozen` — over budget, `isPremium = { false }` →
  resets, `streakFreezeLastUsed` untouched.

### `CurrencyRepositoryTest` (new case)
- `streakFreezeReady reflects the recharge window` — `streakFreezeLastUsed` empty → ready;
  set to today → not ready.

### `StreakFreezeTest` (new, pure, commonTest)
- `isRechargedOn`: empty → true; today → false; 6 days ago → false; 7 days ago → true;
  unparseable → true.

### `ScreenTimeMigrationTest` (`MigrationTestHelper`, desktop)
- `6 to 7 adds the streakFreezeLastUsed column` — `createDatabase(6)`,
  `runMigrationsAndValidate(7, listOf(MIGRATION_6_7))`, assert a row with the new column
  inserts and the old-shape insert (without it) uses the default.
- extend the full-chain test to `runMigrationsAndValidate(7, questLogMigrations.toList())`.

### Fakes to update
- Every `CurrencyDao` fake — `FakeCurrencyDao` (`CalculateDetoxRewardsUseCaseTest`,
  `DashboardViewModelTest`), `StubCurrencyDao` (`GetDashboardStatsUseCaseTest`),
  `FreshInstallCurrencyDao` (`CurrencyRepositoryTest`) — add a `setStreakFreezeUsed`
  override.
- `CurrencyBalance(...)` and `PlayerStats(...)` gain a defaulted field, so named-argument
  constructions compile unchanged. No test builds `PlayerStats` positionally today; if
  that changes during implementation, add `streakFreezeReady = false`.

## Files touched

**New:** `domain/PremiumStatusProvider.kt`, `util/StreakFreeze.kt`,
`util/StreakFreezeTest.kt` (commonTest).

**Modified (shared):** `CalculateDetoxRewardsUseCase.kt`, `SharedModule.kt`,
`data/local/entity/CurrencyBalance.kt`, `data/local/dao/CurrencyDao.kt`,
`data/repository/CurrencyRepository.kt`, `data/local/QuestLogDatabase.kt`,
`data/local/QuestLogMigrations.kt`, `domain/model/PlayerStats.kt`; `schemas/7.json` (generated).

**Modified (app):** `QuestLogApp.kt` (DI), `ui/components/StatsCard.kt`,
`ui/dashboard/DashboardScreen.kt` (pass `isPremium`).

**Modified (tests):** `CalculateDetoxRewardsUseCaseTest.kt`, `CurrencyRepositoryTest.kt`,
`GetDashboardStatsUseCaseTest.kt`, `DashboardViewModelTest.kt`, `ScreenTimeMigrationTest.kt`.

## Rollout

One PR, following the session's established flow (branch → PR → CI → merge). No feature
flag — non-premium behaviour is unchanged and premium is only reachable via the demo
button today. README's "Configuration" section gains a line about the Pro perks; the
`CLAUDE.md` invariants list is unaffected.
