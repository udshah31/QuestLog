# Changelog

All notable changes to QuestLog are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The app has not shipped a versioned release yet — everything lives under **Unreleased**.
For history before this file, see the git log.

## [Unreleased]

### Added

- **2× XP Multiplier Pro perk** — while `BillingManager.isPremium`, XP and gold earned from
  detox saved-time are doubled. The multiplier stacks *multiplicatively* on the streak
  multiplier (a 3× streak plus Pro pays 6×). Flat daily-quest rewards are deliberately
  unaffected, and this is pinned by a regression test. (#19, #21)
- **Streak Freeze Shield Pro perk** — a premium user who goes over the daily budget on a day
  that would break their detox streak keeps the streak instead. One charge, recharging seven
  days after it is spent (`StreakFreeze.COOLDOWN_DAYS`). A charge is spent only when it
  actually rescues a non-zero streak. (#19)
- **Dashboard perk chips** on `StatsCard` — `⚡ 2× XP` and `🛡️ SHIELD READY` /
  `🛡️ RECHARGING`, shown only while Pro is active. (#19)
- **`PremiumStatusProvider`** domain fun-interface — carries `BillingManager` premium state
  into the shared module. Bound in `appModule`, resolved with `getOrNull` in `SharedModule`
  so non-app builds fall back to non-premium. Covered by a plain-JVM DI-seam test. (#19, #20)
- **`## Deploy` runbook** in the README — release keystore, Play service account, and the
  six repo secrets the internal-track workflow needs. (#23)
- **Automatic `versionCode`** derived from the git commit count via `providers.exec`
  (configuration-cache safe); `ANDROID_VERSION_CODE` overrides for a one-off. (#24)

### Changed

- **Room schema v6 → v7** — `MIGRATION_6_7` adds the `currency_balance.streakFreezeLastUsed`
  column (high-water mark for the freeze cooldown). (#19)
- **`REVENUECAT_API_KEY`** for debug and release builds now resolves from the
  `REVENUECAT_API_KEY` env var (CI) or `keystore.properties` `revenueCatKey` (local),
  falling back to the placeholder — it was a hardcoded placeholder before. (#22)

### Fixed

- Streak Freeze no longer spends a charge when the streak is already 0 — previously it would
  fire on every over-budget day-rollover for a non-detoxing premium user, locking the shield
  for seven days while protecting nothing. (#19)
- `StreakFreeze.isRechargedOn` now fails *open* on a future-dated key (device clock moved
  backward), consistent with its empty / unparseable handling. (#19)
