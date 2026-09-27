# Streak-Milestone Pro Trial — Design

**Status:** approved design, ready for planning
**Date:** 2026-09-27

## Goal

The first time a non-Pro player's streak reaches **7 days**, the Pro paywall slides up
**once**, with milestone copy. Price and free-trial text come from the current RevenueCat
**Offering**, and the button makes a **real purchase**.

## Non-goals

- Multiple milestones (3/14/30…). One moment, shown once ever.
- RevenueCat Paywalls (`purchases-ui`), Experiments code, analytics events. Once the price
  comes from the Offering, an Experiment is a dashboard-only change.
- Any change to detox, streak, reward or quest logic in `shared`.
- Persisting the flag in Room — no migration (the Progress spec owns the next one).

## Background

- The paywall does not purchase today. Its button sends `DashboardIntent.UnlockProDemo`,
  which calls `billingManager.setDebugPremium(true)`; the price `"$4.99 / month"` is a
  hardcoded string in `PaywallScreen`.
- `BillingManager.purchasePackage(activity, pkg, onSuccess, onError)` exists but has no
  caller. The app never fetches Offerings.
- `BillingManager.isPremium` is updated by `updatedCustomerInfoListener`, so a successful
  purchase already reaches `DashboardUiState.isPremium` through the ViewModel's `combine`.
- `DashboardUiState.stats.consecutiveDetoxDays` is the streak.
- The paywall is hosted in `ui/QuestLogRoot.kt` behind `state.showPaywall`.
- RevenueCat `purchases` 10.15.1 API used here (checked against the AAR):
  `Purchases.awaitOfferings()`, `Offering.monthly` / `availablePackages`,
  `StoreProduct.price.formatted`, `StoreProduct.period`,
  `StoreProduct.defaultOption?.freePhase?.billingPeriod`, `Period.value` / `Period.unit`
  (`DAY`, `WEEK`, `MONTH`, `YEAR`).

## Design

### 1. `BillingManager` — load the offer

```kotlin
data class ProOffer(val pkg: Package, val priceText: String, val trialText: String?)

suspend fun loadProOffer(): ProOffer?
```

- `awaitOfferings().current`, package = `monthly ?: availablePackages.firstOrNull()`.
- `priceText` = `"${price.formatted} / ${periodLabel(product.period)}"`, e.g. `"$4.99 / month"`.
  No period (non-subscription) → just `price.formatted`.
- `trialText` = `defaultOption?.freePhase?.billingPeriod` formatted as `"7 days free"`,
  `"1 week free"`. **Null when the user isn't trial-eligible** — Google Play omits the free
  phase — so the UI falls back to non-trial copy with no eligibility code of our own.
- Any exception, no current Offering, or no package → `null`.
- `purchasePackage` is unchanged; it finally gets a caller.

Pure formatting lives in a top-level `billing/OfferText.kt`:
`periodLabel(value: Int, unit: Period.Unit): String` → `"month"`, `"3 months"`, `"week"`,
`"7 days"` (singular value omits the number for the price line), and
`trialLabel(value, unit)` → `"7 days free"`, `"1 week free"`, `"1 month free"`.

### 2. `MilestoneOfferStore` — "shown once"

```kotlin
open class MilestoneOfferStore(context: Context) {
    open val shown: Boolean
    open fun markShown()
}
```

A SharedPreferences file `questlog_prefs`, key `milestone_trial_shown`. `open` so
`DashboardViewModelTest` can stub it (the repo's pattern for `ScreenTimeRepository`).
Registered as a Koin `single` in `QuestLogApp` and injected into `DashboardViewModel`.

### 3. `DashboardViewModel` — trigger, offer, purchase

State additions:

```kotlin
enum class PaywallReason { Manual, Milestone }

val paywallReason: PaywallReason = PaywallReason.Manual,
val proOffer: ProOffer? = null,
val purchasing: Boolean = false,
```

- **Trigger.** Inside the existing `combine` collector, after state is updated: if
  `stats.consecutiveDetoxDays >= MILESTONE_DAYS (7) && !isPremium && !store.shown`, then
  `store.markShown()` and open the paywall with `reason = Milestone`.
  - `>= 7`, not `== 7`: a player already past 7 when this ships sees it once.
  - Marked on *show*, not on dismiss — a crash or back-press never re-nags.
  - The check lives in the collector, which only runs with real stats — never the default
    zero state.
- **Opening the paywall** (both reasons) launches `loadProOffer()` and stores the result in
  `proOffer`. `OpenPaywall` sets `reason = Manual`. The offer is re-fetched on each open
  (the SDK caches Offerings; no cache of our own).
- **New intent `BuyPro(activity: Activity)`.** Requires `proOffer != null`; sets
  `purchasing = true`, calls `purchasePackage`.
  - success → `showPaywall = false`, snackbar `"Welcome to QuestLog Pro."`
    (entitlement arrives via the existing listener).
  - user cancelled → `purchasing = false`, nothing else.
  - error → `purchasing = false`, snackbar `"Purchase didn't go through."`
- **`UnlockProDemo` stays** and is used by the button only when `proOffer == null` and
  `BuildConfig.DEBUG`.

### 4. `PaywallScreen`

New params: `reason: PaywallReason`, `offer: ProOffer?`, `purchasing: Boolean`,
`onBuy: () -> Unit` (caller resolves the Activity from `LocalContext`), plus the existing
`onDismiss` and `onUnlockPro` (demo).

| Case | Eyebrow / headline | Sub-line |
|---|---|---|
| Milestone, trial | "7-day streak" / "Seven days kept." | "Your realm earned a trial: {trialText}, then {priceText}." |
| Milestone, no trial | "7-day streak" / "Seven days kept." | "Keep the whole realm: {priceText}." |
| Manual | unchanged ("Architect of the High Realm" / "Keep the whole realm, not half of it.") | — |

Button:

| State | Label | Action |
|---|---|---|
| offer, trial | "Start free trial" | `onBuy` |
| offer, no trial | "Unlock — {priceText}" | `onBuy` |
| no offer, debug | "Unlock — demo" | `onUnlockPro` |
| no offer, release | "Pro unavailable right now" (disabled) | — |
| `purchasing` | same label, disabled | — |

With a trial, a caption under the button: "Cancel anytime before the trial ends." The
"Local receipt validation…" caption is removed (not accurate for a real purchase).
Colours stay on `QuestLogTheme.colors` tokens; no new tokens.

## Error handling

- Offering fetch failure → `proOffer = null` → demo (debug) or disabled (release). Never
  a crash, never a dead-end without a "Maybe later".
- Purchase error → snackbar; paywall stays open to retry.
- `Purchases` not configured (unit tests / previews) → `loadProOffer()` catches and
  returns `null`, same as `BillingManager.init` today.

## External setup (manual, not code)

1. Play Console: a subscription (e.g. `questlog_pro`) with a monthly base plan and a
   **7-day free-trial offer** on it.
2. RevenueCat: product attached to the `pro` entitlement, in a package on the **current**
   Offering.
3. A licence-tester account on an internal test track for sandbox purchases.

## Testing

- `OfferTextTest` (app unit test): `periodLabel` / `trialLabel` for DAY/WEEK/MONTH/YEAR,
  singular and plural.
- `DashboardViewModelTest` with a stub `MilestoneOfferStore`:
  - streak 7, not Pro, not shown → `showPaywall`, `reason == Milestone`, `markShown` once.
  - streak 6 → no paywall.
  - streak 7, Pro → no paywall.
  - streak 7, already shown → no paywall.
  - `OpenPaywall` → `reason == Manual`.
- Existing `DashboardViewModelTest` constructors gain the store param.
- Real purchase: manual check on a debug build against RevenueCat sandbox (not in CI).
