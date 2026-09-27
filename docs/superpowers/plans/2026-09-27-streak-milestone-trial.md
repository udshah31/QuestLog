# Streak-Milestone Pro Trial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first time a non-Pro player's streak reaches 7 days, open the Pro paywall once with milestone copy; price and free-trial text come from the current RevenueCat Offering, and the button makes a real purchase.

**Architecture:** All changes are in the `app` module. `BillingManager` gains `loadProOffer()` (Offering → `ProOffer`) and an `entitlementsKnown` flag; a SharedPreferences-backed `MilestoneOfferStore` remembers "shown once"; `DashboardViewModel` fires the trigger from its existing `combine`, loads the offer when the paywall opens, and handles a new `BuyPro` intent; `PaywallScreen` renders from plain strings via a pure `paywallButton(...)` helper.

**Tech Stack:** Kotlin, Jetpack Compose, RevenueCat `purchases` 10.15.1, Koin, JUnit4 + kotlinx-coroutines-test + mockito-kotlin.

**Spec:** `docs/superpowers/specs/2026-09-27-streak-milestone-trial-design.md`

## Global Constraints

- Milestone threshold: `consecutiveDetoxDays >= 7`, shown **once ever** (`markShown()` on show, not on dismiss).
- SharedPreferences file `questlog_prefs`, key `milestone_trial_shown`. No Room change, no migration.
- Package choice: `offering.current.monthly ?: availablePackages.firstOrNull()`.
- Trial eligibility is not computed in code: no `defaultOption.freePhase` ⇒ `trialText = null` ⇒ non-trial copy.
- Demo unlock only when there is no offer **and** `BuildConfig.DEBUG`. Release with no offer ⇒ disabled "Pro unavailable right now".
- Copy (verbatim): headline `"Seven days kept."`, eyebrow `"7-day streak"`, trial sub-line `"Your realm earned a trial: {trialText}, then {priceText}."`, no-trial sub-line `"Keep the whole realm: {priceText}."`, buttons `"Start free trial"` / `"Unlock — {priceText}"` / `"Unlock — demo"` / `"Pro unavailable right now"` / `"Loading price…"`, caption `"Cancel anytime before the trial ends."`, snackbars `"Welcome to QuestLog Pro."` / `"Purchase didn't go through."`.
- Colour only from `QuestLogTheme.colors`; no raw `Color(...)`, no new tokens.
- No new dependencies. CI runs `:shared:desktopTest`, `:app:testDebugUnitTest`, `:app:assembleDebug` — all three must stay green.
- Use `--no-daemon` on every Gradle command.

## Review Focus

1. **Pro subscriber whose CustomerInfo hasn't loaded yet** — `isPremium` starts `false`; the trigger must wait for `entitlementsKnown`, or a paying user gets a trial paywall (and the flag is burned). Test in Task 3.
2. **Paywall already open (manual) when streak crosses 7** — the trigger must not hijack it or burn the flag; it fires on a later emission. Test in Task 3.
3. **Double-tap on the buy button** — a second `BuyPro` while `purchasing` must not start a second purchase. Test in Task 4.
4. **Offer still loading** — the button must not claim "Pro unavailable" during the fetch; it shows "Loading price…" disabled. Test in Task 5 (`paywallButton`).
5. **Streak broken and rebuilt to 7** — no second showing. Test in Task 3 (already-shown case).

---

## File map

| File | Change |
|---|---|
| `app/src/main/java/com/example/questlog/billing/OfferText.kt` | **Create** — pure `periodLabel` / `trialLabel` |
| `app/src/main/java/com/example/questlog/billing/BillingManager.kt` | **Modify** — `ProOffer`, `loadProOffer()`, `entitlementsKnown` |
| `app/src/main/java/com/example/questlog/billing/MilestoneOfferStore.kt` | **Create** — SharedPreferences flag |
| `app/src/main/java/com/example/questlog/QuestLogApp.kt` | **Modify** — Koin: store single, VM gets 7th arg |
| `app/src/main/java/com/example/questlog/ui/dashboard/DashboardViewModel.kt` | **Modify** — state fields, trigger, offer load, `BuyPro` |
| `app/src/main/java/com/example/questlog/ui/paywall/PaywallScreen.kt` | **Modify** — new params, milestone copy, `paywallButton` |
| `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt` | **Modify** — pass new params, resolve Activity |
| `app/src/test/java/com/example/questlog/billing/OfferTextTest.kt` | **Create** |
| `app/src/test/java/com/example/questlog/billing/BillingManagerTest.kt` | **Create** |
| `app/src/test/java/com/example/questlog/ui/dashboard/DashboardViewModelTest.kt` | **Modify** — `FakeMilestoneStore`, new tests |
| `app/src/test/java/com/example/questlog/ui/paywall/PaywallButtonTest.kt` | **Create** |
| `README.md`, `CLAUDE.md`, spec | **Modify** — docs (Task 6) |

---

### Task 1: Offer text formatting

**Files:**
- Create: `app/src/main/java/com/example/questlog/billing/OfferText.kt`
- Test: `app/src/test/java/com/example/questlog/billing/OfferTextTest.kt`

**Interfaces:**
- Produces: `fun periodLabel(value: Int, unit: Period.Unit): String?`, `fun trialLabel(value: Int, unit: Period.Unit): String?` (both in package `com.example.questlog.billing`; `Period.Unit` is `com.revenuecat.purchases.models.Period.Unit`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.questlog.billing

import com.revenuecat.purchases.models.Period
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfferTextTest {
    @Test fun `period label omits a single unit's number`() {
        assertEquals("month", periodLabel(1, Period.Unit.MONTH))
        assertEquals("week", periodLabel(1, Period.Unit.WEEK))
        assertEquals("year", periodLabel(1, Period.Unit.YEAR))
        assertEquals("day", periodLabel(1, Period.Unit.DAY))
    }

    @Test fun `period label pluralises`() {
        assertEquals("3 months", periodLabel(3, Period.Unit.MONTH))
        assertEquals("7 days", periodLabel(7, Period.Unit.DAY))
    }

    @Test fun `trial label always carries the number`() {
        assertEquals("7 days free", trialLabel(7, Period.Unit.DAY))
        assertEquals("1 week free", trialLabel(1, Period.Unit.WEEK))
        assertEquals("1 month free", trialLabel(1, Period.Unit.MONTH))
    }

    @Test fun `unknown unit yields null`() {
        assertNull(periodLabel(1, Period.Unit.UNKNOWN))
        assertNull(trialLabel(7, Period.Unit.UNKNOWN))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.billing.OfferTextTest"`
Expected: FAIL — compilation error, `periodLabel` / `trialLabel` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.questlog.billing

import com.revenuecat.purchases.models.Period

private fun unitName(unit: Period.Unit): String? = when (unit) {
    Period.Unit.DAY -> "day"
    Period.Unit.WEEK -> "week"
    Period.Unit.MONTH -> "month"
    Period.Unit.YEAR -> "year"
    else -> null
}

private fun counted(value: Int, name: String) = "$value $name${if (value == 1) "" else "s"}"

/** Billing period for a price line: "month", "3 months". Null for an unknown unit. */
fun periodLabel(value: Int, unit: Period.Unit): String? =
    unitName(unit)?.let { if (value == 1) it else counted(value, it) }

/** Free-trial length: "7 days free", "1 week free". Null for an unknown unit. */
fun trialLabel(value: Int, unit: Period.Unit): String? =
    unitName(unit)?.let { "${counted(value, it)} free" }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.billing.OfferTextTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/billing/OfferText.kt app/src/test/java/com/example/questlog/billing/OfferTextTest.kt
git commit -m "Billing: period and trial label formatting"
```

---

### Task 2: `BillingManager` — load the Pro offer, know when entitlements are known

**Files:**
- Modify: `app/src/main/java/com/example/questlog/billing/BillingManager.kt`
- Test: `app/src/test/java/com/example/questlog/billing/BillingManagerTest.kt`

**Interfaces:**
- Consumes: `periodLabel`, `trialLabel` (Task 1).
- Produces:
  - `data class ProOffer(val pkg: Package, val priceText: String, val trialText: String?)` (package `com.example.questlog.billing`)
  - `suspend fun BillingManager.loadProOffer(): ProOffer?`
  - `val BillingManager.entitlementsKnown: StateFlow<Boolean>` — `true` once CustomerInfo has arrived, or after `setDebugPremium(...)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.questlog.billing

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillingManagerTest {
    @Test fun `loadProOffer returns null when Purchases is not configured`() = runTest {
        assertNull(BillingManager().loadProOffer())
    }

    @Test fun `entitlements are unknown until customer info or a debug override arrives`() {
        val billing = BillingManager()
        assertFalse(billing.entitlementsKnown.value)
        billing.setDebugPremium(false)
        assertTrue(billing.entitlementsKnown.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.billing.BillingManagerTest"`
Expected: FAIL — `loadProOffer` / `entitlementsKnown` unresolved.

- [ ] **Step 3: Implement**

Add imports to `BillingManager.kt`:

```kotlin
import com.revenuecat.purchases.awaitOfferings
import kotlinx.coroutines.CancellationException
```

Add above the class:

```kotlin
/** The Pro package to sell, with display strings resolved from the store product. */
data class ProOffer(val pkg: Package, val priceText: String, val trialText: String?)
```

Inside the class, next to `_isPremium`:

```kotlin
    // False until CustomerInfo arrives: isPremium=false before then means "unknown",
    // not "free" — the milestone trigger must not fire on it.
    private val _entitlementsKnown = MutableStateFlow(false)
    val entitlementsKnown: StateFlow<Boolean> = _entitlementsKnown.asStateFlow()
```

In `updateEntitlements`, after `_isPremium.value = hasPro`:

```kotlin
        _entitlementsKnown.value = true
```

New method, after `updateEntitlements`:

```kotlin
    /** The current Offering's monthly (else first) package, or null on any failure. */
    suspend fun loadProOffer(): ProOffer? = try {
        val offering = Purchases.sharedInstance.awaitOfferings().current
        (offering?.monthly ?: offering?.availablePackages?.firstOrNull())?.let { pkg ->
            val product = pkg.product
            val price = product.price.formatted
            val period = product.period?.let { periodLabel(it.value, it.unit) }
            val trial = product.defaultOption?.freePhase?.billingPeriod
                ?.let { trialLabel(it.value, it.unit) }
            ProOffer(pkg, if (period != null) "$price / $period" else price, trial)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // not configured, offline, or no Offering set up
    }
```

`setDebugPremium` becomes:

```kotlin
    fun setDebugPremium(enabled: Boolean) {
        _isPremium.value = enabled
        _entitlementsKnown.value = true
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.billing.BillingManagerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/billing/BillingManager.kt app/src/test/java/com/example/questlog/billing/BillingManagerTest.kt
git commit -m "Billing: load the Pro offer from the current Offering; track entitlementsKnown"
```

---

### Task 3: Milestone trigger (`MilestoneOfferStore` + ViewModel + Koin)

**Files:**
- Create: `app/src/main/java/com/example/questlog/billing/MilestoneOfferStore.kt`
- Modify: `app/src/main/java/com/example/questlog/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/example/questlog/QuestLogApp.kt:23`
- Test: `app/src/test/java/com/example/questlog/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `BillingManager.entitlementsKnown`, `BillingManager.loadProOffer()`, `ProOffer` (Task 2).
- Produces:
  - `open class MilestoneOfferStore(prefs: SharedPreferences)` with `open val shown: Boolean`, `open fun markShown()`
  - `enum class PaywallReason { Manual, Milestone }` (in `DashboardViewModel.kt`)
  - `DashboardUiState` fields: `paywallReason: PaywallReason = PaywallReason.Manual`, `proOffer: ProOffer? = null`, `offerLoading: Boolean = false`, `purchasing: Boolean = false`
  - `DashboardViewModel(..., billingManager: BillingManager, milestoneStore: MilestoneOfferStore)` — new last constructor param
  - `DashboardViewModel.MILESTONE_DAYS = 7` (companion const)

- [ ] **Step 1: Create the store**

```kotlin
package com.example.questlog.billing

import android.content.SharedPreferences

/** Remembers that the 7-day-streak trial paywall has been shown — once, ever. */
open class MilestoneOfferStore(private val prefs: SharedPreferences) {
    open val shown: Boolean get() = prefs.getBoolean(KEY, false)
    open fun markShown() = prefs.edit().putBoolean(KEY, true).apply()

    companion object {
        const val PREFS = "questlog_prefs"
        private const val KEY = "milestone_trial_shown"
    }
}
```

- [ ] **Step 2: Write the failing tests**

In `DashboardViewModelTest.kt`, add imports:

```kotlin
import com.example.questlog.billing.MilestoneOfferStore
import org.mockito.kotlin.mock
```

Add the fake next to the other fakes (top level):

```kotlin
class FakeMilestoneStore(var shownFlag: Boolean = false) : MilestoneOfferStore(mock()) {
    var markCount = 0
    override val shown: Boolean get() = shownFlag
    override fun markShown() { shownFlag = true; markCount++ }
}
```

Add `milestoneStore = FakeMilestoneStore(),` as the last argument of **every existing** `DashboardViewModel(...)` call in the file (4 calls).

Add inside the test class:

```kotlin
    private fun milestoneVm(
        streak: Int,
        billing: BillingManager = BillingManager().apply { setDebugPremium(false) },
        store: FakeMilestoneStore = FakeMilestoneStore(),
    ): DashboardViewModel {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(consecutiveDetoxDays = streak); flow.value = balance
        }
        val currencyRepo = CurrencyRepository(currencyDao)
        val inventoryRepo = InventoryRepository(FakeInventoryDao())
        return DashboardViewModel(
            getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, emptyBlocklistRepo()),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(
                ScreenTimeRepository(FakeScreenTimeDao(), ScreenTimeTracker()), currencyRepo, { emptyList() },
            ),
            detoxMonitor = silentMonitor(),
            purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo),
            dailyQuestRepo = DailyQuestRepository(FakeQuestDao()),
            billingManager = billing,
            milestoneStore = store,
        )
    }

    @Test
    fun `seven-day streak opens the milestone paywall once`() = runTest {
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, store = store)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showPaywall)
        assertEquals(PaywallReason.Milestone, vm.uiState.value.paywallReason)
        assertEquals(1, store.markCount)
    }

    @Test
    fun `six-day streak does not open the paywall`() = runTest {
        val vm = milestoneVm(streak = 6)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
    }

    @Test
    fun `pro players never see the milestone paywall`() = runTest {
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = BillingManager().apply { setDebugPremium(true) }, store = store)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
        assertEquals(0, store.markCount)
    }

    @Test
    fun `already-shown milestone is not shown again`() = runTest {
        val vm = milestoneVm(streak = 12, store = FakeMilestoneStore(shownFlag = true))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
    }

    @Test
    fun `milestone waits until entitlements are known`() = runTest {
        val billing = BillingManager() // entitlementsKnown = false: CustomerInfo not in yet
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = billing, store = store)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
        assertEquals(0, store.markCount)

        billing.setDebugPremium(false) // CustomerInfo arrives: not Pro
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showPaywall)
        assertEquals(1, store.markCount)
    }

    @Test
    fun `manual paywall is not hijacked and does not burn the milestone`() = runTest {
        val billing = BillingManager()
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = billing, store = store)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        billing.setDebugPremium(false)
        advanceUntilIdle()
        assertEquals(PaywallReason.Manual, vm.uiState.value.paywallReason)
        assertEquals(0, store.markCount)
    }

    @Test
    fun `opening the paywall manually loads the offer and settles`() = runTest {
        val vm = milestoneVm(streak = 0)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.showPaywall)
        assertEquals(PaywallReason.Manual, s.paywallReason)
        assertFalse(s.offerLoading)
        assertEquals(null, s.proOffer) // Purchases isn't configured in unit tests
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — compilation errors (`milestoneStore` param, `PaywallReason`, `offerLoading` unresolved).

- [ ] **Step 4: Implement in `DashboardViewModel.kt`**

Imports:

```kotlin
import com.example.questlog.billing.MilestoneOfferStore
import com.example.questlog.billing.ProOffer
```

Above `DashboardUiState`:

```kotlin
enum class PaywallReason { Manual, Milestone }
```

Add to `DashboardUiState` after `showPaywall`:

```kotlin
    val paywallReason: PaywallReason = PaywallReason.Manual,
    val proOffer: ProOffer? = null,
    val offerLoading: Boolean = false,
    val purchasing: Boolean = false,
```

Constructor — add last param:

```kotlin
    private val billingManager: BillingManager,
    private val milestoneStore: MilestoneOfferStore,
) : ViewModel() {

    companion object {
        const val MILESTONE_DAYS = 7
    }
```

Replace the first `combine` block in `init` with:

```kotlin
        viewModelScope.launch {
            combine(
                getDashboardStats(),
                dailyQuestRepo.observeToday(),
                billingManager.isPremium,
                billingManager.entitlementsKnown,
            ) { dashboardState, quests, isPremium, entitlementsKnown ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        stats = dashboardState.stats,
                        cityTiles = dashboardState.cityTiles,
                        dailyQuests = quests,
                        blockedAppCount = dashboardState.blockedAppCount,
                        isPremium = isPremium,
                    )
                }
                maybeOfferMilestone(dashboardState.stats.consecutiveDetoxDays, isPremium, entitlementsKnown)
            }.collect {}
        }
```

Add private helpers at the bottom of the class:

```kotlin
    /** First 7-day streak for a known-free player: open the trial paywall, once ever. */
    private fun maybeOfferMilestone(streak: Int, isPremium: Boolean, entitlementsKnown: Boolean) {
        if (streak < MILESTONE_DAYS || isPremium || !entitlementsKnown) return
        // Don't hijack a paywall the player opened themselves; try again on a later emission.
        if (_uiState.value.showPaywall || milestoneStore.shown) return
        milestoneStore.markShown()
        openPaywall(PaywallReason.Milestone)
    }

    private fun openPaywall(reason: PaywallReason) {
        _uiState.update { it.copy(showPaywall = true, paywallReason = reason, offerLoading = true) }
        viewModelScope.launch {
            val offer = billingManager.loadProOffer()
            _uiState.update { it.copy(proOffer = offer, offerLoading = false) }
        }
    }
```

In `onIntent`, replace the two direct `showPaywall = true` updates:

- `is PurchaseResult.PremiumRequired -> { _uiState.update { it.copy(showPaywall = true) } }` → `is PurchaseResult.PremiumRequired -> openPaywall(PaywallReason.Manual)`
- `is DashboardIntent.OpenPaywall -> { _uiState.update { it.copy(showPaywall = true) } }` → `is DashboardIntent.OpenPaywall -> openPaywall(PaywallReason.Manual)`

- [ ] **Step 5: Wire Koin in `QuestLogApp.kt`**

Imports:

```kotlin
import android.content.Context
import com.example.questlog.billing.MilestoneOfferStore
```

In `appModule`:

```kotlin
    single { MilestoneOfferStore(androidContext().getSharedPreferences(MilestoneOfferStore.PREFS, Context.MODE_PRIVATE)) }
    viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get(), get()) }
```

(`androidContext` is already imported from `org.koin.android.ext.koin`.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: PASS — all app unit tests, including the 7 new ones and `PremiumStatusProviderSeamTest` (the store `single` is lazy, so no Android context is needed there).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/questlog/billing/MilestoneOfferStore.kt app/src/main/java/com/example/questlog/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/example/questlog/QuestLogApp.kt app/src/test/java/com/example/questlog/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Open the Pro paywall once on the first 7-day streak; load the Offering on open"
```

---

### Task 4: `BuyPro` intent — real purchase

**Files:**
- Modify: `app/src/main/java/com/example/questlog/ui/dashboard/DashboardViewModel.kt`
- Test: `app/src/test/java/com/example/questlog/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `BillingManager.purchasePackage(activity, pkg, onSuccess, onError)` (existing), `DashboardUiState.proOffer` / `purchasing` (Task 3).
- Produces: `data class DashboardIntent.BuyPro(val activity: Activity) : DashboardIntent`.

- [ ] **Step 1: Write the failing tests**

Add import `import android.app.Activity`. Add tests:

```kotlin
    @Test
    fun `buy pro without an offer is a no-op`() = runTest {
        val vm = milestoneVm(streak = 0)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.BuyPro(mock<Activity>()))
        assertFalse(vm.uiState.value.purchasing)
        assertTrue(vm.uiState.value.showPaywall)
    }
```

The double-tap guard (Review Focus 3) is exercised by construction: `BuyPro` returns early while `purchasing` is true. It can't be driven end-to-end in a JVM test because `Package` requires a live store product; the guard is the first line of the branch (Step 3) and is checked in review.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardIntent.BuyPro` unresolved.

- [ ] **Step 3: Implement**

Import `android.app.Activity`. In `DashboardIntent`:

```kotlin
    data class BuyPro(val activity: Activity) : DashboardIntent
```

In `onIntent`:

```kotlin
            is DashboardIntent.BuyPro -> {
                val state = _uiState.value
                val offer = state.proOffer ?: return
                if (state.purchasing) return // double-tap guard
                _uiState.update { it.copy(purchasing = true) }
                billingManager.purchasePackage(
                    activity = intent.activity,
                    pkg = offer.pkg,
                    onSuccess = {
                        // Entitlement reaches isPremium via BillingManager's listener.
                        _uiState.update {
                            it.copy(purchasing = false, showPaywall = false, snackbarMessage = "Welcome to QuestLog Pro.")
                        }
                    },
                    onError = { _, userCancelled ->
                        _uiState.update {
                            it.copy(
                                purchasing = false,
                                snackbarMessage = if (userCancelled) it.snackbarMessage else "Purchase didn't go through.",
                            )
                        }
                    },
                )
            }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/example/questlog/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Paywall: BuyPro intent purchases the Offering's package"
```

---

### Task 5: `PaywallScreen` — milestone copy, live price, button states

**Files:**
- Modify: `app/src/main/java/com/example/questlog/ui/paywall/PaywallScreen.kt`
- Modify: `app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt:141-146`
- Test: `app/src/test/java/com/example/questlog/ui/paywall/PaywallButtonTest.kt`

**Interfaces:**
- Consumes: `PaywallReason` (Task 3), `DashboardIntent.BuyPro` (Task 4), `DashboardUiState.proOffer / offerLoading / purchasing / paywallReason`.
- Produces:
  - `internal enum class PaywallAction { Buy, Demo, None }`
  - `internal data class PaywallButton(val label: String, val action: PaywallAction)`
  - `internal fun paywallButton(priceText: String?, trialText: String?, offerLoading: Boolean, demoAvailable: Boolean): PaywallButton`
  - `PaywallScreen(reason, priceText, trialText, offerLoading, purchasing, demoAvailable, onBuy, onUnlockDemo, onDismiss, modifier)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.questlog.ui.paywall

import org.junit.Test
import kotlin.test.assertEquals

class PaywallButtonTest {
    @Test fun `trial offer`() = assertEquals(
        PaywallButton("Start free trial", PaywallAction.Buy),
        paywallButton("$4.99 / month", "7 days free", offerLoading = false, demoAvailable = false),
    )

    @Test fun `offer without trial shows the price`() = assertEquals(
        PaywallButton("Unlock — $4.99 / month", PaywallAction.Buy),
        paywallButton("$4.99 / month", null, offerLoading = false, demoAvailable = false),
    )

    @Test fun `loading never claims unavailable`() = assertEquals(
        PaywallButton("Loading price…", PaywallAction.None),
        paywallButton(null, null, offerLoading = true, demoAvailable = false),
    )

    @Test fun `no offer in a debug build falls back to the demo`() = assertEquals(
        PaywallButton("Unlock — demo", PaywallAction.Demo),
        paywallButton(null, null, offerLoading = false, demoAvailable = true),
    )

    @Test fun `no offer in a release build is disabled`() = assertEquals(
        PaywallButton("Pro unavailable right now", PaywallAction.None),
        paywallButton(null, null, offerLoading = false, demoAvailable = false),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.questlog.ui.paywall.PaywallButtonTest"`
Expected: FAIL — `paywallButton` unresolved.

- [ ] **Step 3: Implement in `PaywallScreen.kt`**

Add import `import com.example.questlog.ui.dashboard.PaywallReason`. Add above `PaywallScreen`:

```kotlin
internal enum class PaywallAction { Buy, Demo, None }
internal data class PaywallButton(val label: String, val action: PaywallAction)

internal fun paywallButton(
    priceText: String?,
    trialText: String?,
    offerLoading: Boolean,
    demoAvailable: Boolean,
): PaywallButton = when {
    priceText != null && trialText != null -> PaywallButton("Start free trial", PaywallAction.Buy)
    priceText != null -> PaywallButton("Unlock — $priceText", PaywallAction.Buy)
    offerLoading -> PaywallButton("Loading price…", PaywallAction.None)
    demoAvailable -> PaywallButton("Unlock — demo", PaywallAction.Demo)
    else -> PaywallButton("Pro unavailable right now", PaywallAction.None)
}
```

Replace the `PaywallScreen` signature:

```kotlin
@Composable
fun PaywallScreen(
    reason: PaywallReason,
    priceText: String?,
    trialText: String?,
    offerLoading: Boolean,
    purchasing: Boolean,
    demoAvailable: Boolean,
    onBuy: () -> Unit,
    onUnlockDemo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Replace the headline `Column` (eyebrow + hero line) with:

```kotlin
            Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
                val milestone = reason == PaywallReason.Milestone
                Text(
                    (if (milestone) "7-day streak" else "Architect of the High Realm").uppercase(),
                    style = QuestType.label,
                    color = c.earned,
                )
                Text(
                    if (milestone) "Seven days kept." else "Keep the whole realm, not half of it.",
                    style = QuestType.heroLine,
                    color = c.inkPrimary,
                )
                if (milestone && priceText != null) {
                    Text(
                        if (trialText != null) "Your realm earned a trial: $trialText, then $priceText."
                        else "Keep the whole realm: $priceText.",
                        style = QuestType.bodyLarge,
                        color = c.inkSecondary,
                    )
                }
            }
```

Replace the `Button(...) { Text("Unlock — \$4.99 / month", ...) }` block with:

```kotlin
            val button = paywallButton(priceText, trialText, offerLoading, demoAvailable)
            Button(
                onClick = {
                    when (button.action) {
                        PaywallAction.Buy -> onBuy()
                        PaywallAction.Demo -> onUnlockDemo()
                        PaywallAction.None -> Unit
                    }
                },
                enabled = button.action != PaywallAction.None && !purchasing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = QuestShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.earned,
                    contentColor = c.ground,
                ),
            ) {
                Text(button.label, style = QuestType.bodyLarge)
            }

            if (trialText != null && priceText != null) {
                Text(
                    "Cancel anytime before the trial ends.",
                    style = QuestType.caption,
                    color = c.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
```

Delete the `"Local receipt validation via RevenueCat. Works offline."` `Text`.

Replace the preview with two previews:

```kotlin
@Preview(name = "Paywall — manual, loading")
@Composable
private fun PaywallScreenPreview() {
    QuestLogTheme {
        PaywallScreen(
            reason = PaywallReason.Manual, priceText = null, trialText = null,
            offerLoading = true, purchasing = false, demoAvailable = false,
            onBuy = {}, onUnlockDemo = {}, onDismiss = {},
        )
    }
}

@Preview(name = "Paywall — milestone, trial")
@Composable
private fun PaywallMilestonePreview() {
    QuestLogTheme {
        PaywallScreen(
            reason = PaywallReason.Milestone, priceText = "$4.99 / month", trialText = "7 days free",
            offerLoading = false, purchasing = false, demoAvailable = false,
            onBuy = {}, onUnlockDemo = {}, onDismiss = {},
        )
    }
}
```

(Remove the now-unused `android.content.res.Configuration` import; the dark preview is dropped — the theme is light-only.)

- [ ] **Step 4: Wire it in `QuestLogRoot.kt`**

Add imports `import android.app.Activity` and `import com.example.questlog.BuildConfig` (QuestLogRoot is in `com.example.questlog.ui`, so `BuildConfig` needs the import). Replace the `PaywallScreen(...)` call:

```kotlin
            val activity = LocalContext.current as Activity
            PaywallScreen(
                reason = state.paywallReason,
                priceText = state.proOffer?.priceText,
                trialText = state.proOffer?.trialText,
                offerLoading = state.offerLoading,
                purchasing = state.purchasing,
                demoAvailable = BuildConfig.DEBUG,
                onBuy = { viewModel.onIntent(DashboardIntent.BuyPro(activity)) },
                onUnlockDemo = { viewModel.onIntent(DashboardIntent.UnlockProDemo) },
                onDismiss = { viewModel.onIntent(DashboardIntent.DismissPaywall) },
            )
```

(`LocalContext` is already imported; `setContent` in `MainActivity` makes it the Activity.)

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/questlog/ui/paywall/PaywallScreen.kt app/src/main/java/com/example/questlog/ui/QuestLogRoot.kt app/src/test/java/com/example/questlog/ui/paywall/PaywallButtonTest.kt
git commit -m "Paywall: milestone copy, live Offering price and trial, real purchase button"
```

---

### Task 6: Docs + full CI check

**Files:**
- Modify: `README.md` (Configuration → **Pro perks** bullet; app-screens paragraph says "Pro paywall dialog")
- Modify: `CLAUDE.md` (Invariants / gotchas)
- Modify: `docs/superpowers/specs/2026-09-27-streak-milestone-trial-design.md`

- [ ] **Step 1: README** — after the **Pro perks** bullet add:

```markdown
- **Pro paywall**: price and free-trial text come from the current RevenueCat Offering
  (`BillingManager.loadProOffer`, monthly package else first). The first time a free player's
  streak reaches 7 days the paywall opens once with trial copy (`MilestoneOfferStore`,
  SharedPreferences `questlog_prefs`). With no Offering, debug builds offer a demo unlock;
  release builds disable the button.
```

and change "the Pro paywall dialog" to "the Pro paywall screen" in the screens paragraph.

- [ ] **Step 2: CLAUDE.md** — add under *Invariants / gotchas*:

```markdown
- `BillingManager.isPremium == false` means "unknown" until `entitlementsKnown` is true —
  gate anything that treats the player as free (the 7-day milestone paywall) on it.
  The milestone flag lives in SharedPreferences (`MilestoneOfferStore`), not Room.
```

- [ ] **Step 3: Spec** — in section 3's *Trigger* bullet list add:
  `- Also requires BillingManager.entitlementsKnown (CustomerInfo arrived), so a Pro user on a cold start isn't shown a trial.`
  and in section 4 replace `offer: ProOffer?` with `priceText: String?, trialText: String?, offerLoading: Boolean, demoAvailable: Boolean` (previewable without a `Package`), and add the `"Loading price…"` row to the button table.

- [ ] **Step 4: Run the full CI set**

Run: `./gradlew :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add README.md CLAUDE.md docs/superpowers/specs/2026-09-27-streak-milestone-trial-design.md
git commit -m "Docs: streak-milestone trial paywall"
```

---

## Manual verification (after merge to a test track; not CI)

1. Play Console: subscription with monthly base plan + 7-day free-trial offer; RevenueCat: attached to `pro`, on the current Offering.
2. Debug build, licence-tester account: open paywall from Today's "Get Pro" → live price + "Start free trial".
3. Force a streak ≥ 7 (e.g. `adb shell` + sqlite `UPDATE currency_balance SET consecutiveDetoxDays = 7`), clear app prefs, relaunch → milestone paywall once; relaunch again → not shown.
4. Complete sandbox purchase → paywall closes, "Welcome to QuestLog Pro.", Pro perks active.
