# Changelog

All notable changes to QuestLog are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **All-time stats on Today** — a running **reclaimed-time total** under the daily
  hero (`currency_balance.lifetimeSavedMs`, folded in on each day rollover; DB v9)
  and an **"N apps guarded"** line under the level bar, live from the blocklist.
- **In-app blocklist editor** — a gear on the Today screen opens **Distractions**, a
  searchable list of installed apps. Toggle any app as a distraction and optionally
  give it a daily allowance (Off / 15m / 30m / 1h); only foreground time beyond the
  allowance is subtracted from reclaimed time. Seeded on first run with the seven
  historical defaults (Room migration `v7→v8` for existing installs, an `onCreate`
  callback for fresh ones). The screen surfaces a usage-access permission prompt when
  the permission is missing.
- **Rotating daily-quest pool** — the catalog grew from 3 fixed quests to a pool of 8, of
  which 3 are active each day. `questsForDay(date)` picks them with a sliding window over the
  catalog that advances one slot per day: every quest runs 3 days in every 8, consecutive
  days share 2 of 3, and the set is a pure function of the local date (same on every device).
  Five new quests — Feed Freeze, Century Saver, Budget Guardian, Master Builder, Dawn
  Discipline — all checked against data the repositories already expose. Auto-grant,
  once-per-day, and the flat-reward rule are unchanged.

### Changed

- **Full UI redesign** — the single scrolling dashboard becomes a focused **Today**
  screen with the build grid on its own **Realm** screen. New semantic colour token
  system (`QuestColors` / `QuestLogTheme`), **"Palette #1": charcoal ink on
  paper-white with a single red accent** (gold falls back to grey, Pro/locked reuses
  the red). The light/dark plumbing is kept but both paths resolve to Palette #1 —
  system-bar icons are forced dark. A bundled *Instrument Serif* display face, and
  emoji removed from all persistent UI. No change to rewards, quests, streaks,
  billing, or persistence.
- `DailyQuestRepository` takes an injectable `clock` / `timeZone` (matching
  `EvaluateDailyQuestsUseCase`), so the day's quest set is testable.

## [1.0.0] - 2026-08-28

First tagged release. QuestLog is a gamified digital-detox Android app — a Kotlin
Multiplatform core (`shared`) with a Jetpack Compose UI (`app`). You earn RPG rewards (XP,
gold, levels, day-streaks) for **not** using distracting apps and spend the gold building a
fantasy city; a RevenueCat "Pro" subscription unlocks premium buildings and perks.

It began as a hackathon scaffold in which most systems were stubbed or subtly broken. Every
one is now real and tested (~72 tests, CI green, run end-to-end on an emulator).

### Core loop

- **Saved-time rewards** — `DetoxBudget` scores the day as `min(90 min, elapsed) − flagged
  foreground time`, floored at 0. `TimeConversion` converts that to 10 XP + 2 gold per saved
  minute, scaled by the streak multiplier. The daily grant is idempotent via a high-water
  mark, so repeated polling can never inflate the balance. (#1, #16)
- **Detox streak** — advances once per calendar day on rollover; each day within a 60-minute
  flagged-app budget adds to the multiplier (`1.0×`–`3.0×`), phone-free days included; the
  first over-budget day resets it. (#11)
- **Daily quests** — three fixed quests (`QuestCatalog`) evaluated on every detox tick
  against real usage / inventory, auto-granted once per day, tracked in a `quest_completions`
  table. (#13)
- **Background refresh** — `DetoxMonitorFlow` polls screen-time every 60 s (a failed tick is
  skipped, not fatal); the dashboard updates reactively through `GetDashboardStatsUseCase`.
  (#4, #5)

### Pro perks

- **2× XP Multiplier** — while `BillingManager.isPremium`, XP and gold from detox saved-time
  are doubled. Stacks *multiplicatively* on the streak multiplier (a 3× streak plus Pro pays
  6×). Flat daily-quest rewards are deliberately unaffected, pinned by a regression test.
  (#19, #21)
- **Streak Freeze Shield** — a premium user who goes over the daily budget on a day that
  would break their streak keeps the streak instead. One charge, recharging seven days after
  it is spent (`StreakFreeze.COOLDOWN_DAYS`); a charge is spent only when it actually rescues
  a non-zero streak. (#19)
- **Premium buildings** — Crystal Castle and Aurora Fountain, gated behind Pro. (#19)
- **Dashboard perk chips** on `StatsCard` — `⚡ 2× XP` and `🛡️ SHIELD READY` /
  `🛡️ RECHARGING`, shown only while Pro is active. (#19)
- **`PremiumStatusProvider`** domain fun-interface carries `BillingManager` premium state
  into the shared module; bound in `appModule`, resolved with `getOrNull` in `SharedModule`
  so non-app builds fall back to non-premium. Covered by a plain-JVM DI-seam test. (#19, #20)

### Persistence

- Room (KMP) at schema **v7**, migrations `1 → … → 7`, each exercised through
  `MigrationTestHelper` on the desktop JVM target and validated against the exported schema
  JSON. (#3, #9, #19)
- `MIGRATION_6_7` adds `currency_balance.streakFreezeLastUsed` (freeze-cooldown high-water
  mark). Fixed the fresh-install `UPDATE` no-op on the single-row `currency_balance`. (#2)

### Build & release

- Release signing config — credentials from env vars (CI) or a gitignored
  `keystore.properties` (local); the `release` build stays unsigned and still compiles when
  neither resolves. `proguard-rules.pro` added. (#12, #18)
- `versionCode` derives automatically from the git commit count (`ANDROID_VERSION_CODE`
  overrides); `REVENUECAT_API_KEY` resolves from the env var / `keystore.properties` instead
  of a hardcoded placeholder. (#22, #24)
- `ci.yml` runs both test suites plus `assembleDebug` on every push and PR;
  `deploy-internal.yml` ships a signed AAB to the Play internal track on push to `main` (see
  the README `## Deploy` runbook — pending repo secrets). (#7, #23)

### Fixed

- Infinite-economy exploit — brief app opens no longer farm rewards; the budget rewards
  sustained abstinence. (#1, #16)
- Duplicate `screen_time_records` rows; dashboard "time saved" stuck at 0. (#3, #4)
- Streak Freeze no longer spends a charge at streak 0 (it would fire every over-budget
  rollover for a non-detoxing premium user, locking the shield while protecting nothing);
  `StreakFreeze.isRechargedOn` fails *open* on a backward device clock. (#19)

### Removed

- Unused Navigation3 dependencies; dead `currentLevel` column and no-op schema plumbing.
  (#6, #14, #15)

[Unreleased]: https://github.com/udshah31/QuestLog/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/udshah31/QuestLog/releases/tag/v1.0.0
